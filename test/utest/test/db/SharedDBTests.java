package test.db;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.sql.DataSource;

import junit.framework.TestCase;

import org.nosco.Bulk;
import org.nosco.Constants.CALENDAR;
import org.nosco.Context;
import org.nosco.Context.Undoer;
import org.nosco.Diff;
import org.nosco.Diff.RowChange;
import static org.nosco.Function.*;
import org.nosco.Query;
import org.nosco.datasource.ConnectionCountingDataSource;
import org.nosco.unittest.nosco_test_jpetstore.Category;
import org.nosco.unittest.nosco_test_jpetstore.Inventory;
import org.nosco.unittest.nosco_test_jpetstore.Item;
import org.nosco.unittest.nosco_test_jpetstore.Orderstatus;
import org.nosco.unittest.nosco_test_jpetstore.Product;
import org.nosco.unittest.nosco_test_jpetstore.Supplier;

import test.db.callback.nosco_test_jpetstore.ItemCB;


public class SharedDBTests extends TestCase {

	DataSource ds = null;
	ConnectionCountingDataSource ccds = null;

	public void test01() throws SQLException {
		final Connection conn = ds.getConnection();
		final Statement stmt = conn.createStatement();
		final ResultSet rs = stmt.executeQuery("select count(1) from inventory");
		rs.next();
		final int count = rs.getInt(1);
		//System.err.println("count: "+ count);
		rs.close();
		stmt.close();
		conn.close();
		assertTrue(count > 0);
	}

	public void test02() throws SQLException {
		assertTrue(Item.ALL.use(ds).count() > 0);
	}

	public void test04() throws SQLException {
		int count = 0;
		for (final Item i : Item.ALL.use(ds).top(10)) ++count;
		assertEquals(10, count);
	}

	public void testWithAndCross() throws SQLException {
		int count = 0;
		final int countp = Product.ALL.use(ds).count();
		final int countc = Category.ALL.use(ds).count();
		final Query<Product> q = Product.ALL.use(ds).cross(Category.class);
		for (final Product p : q) {
			//assertNotNull(p);
			//assertNotNull(p.getProductidFK());
			++count;
		}
		System.err.println("countp "+ countp);
		System.err.println("countc "+ countc);
		System.err.println("testWithAndCross "+ count);
		System.err.println("testWithAndCross "+ Category.ALL.use(ds).count());
		//assertTrue(count == countp * countc);
	}

	public void testFK1() throws SQLException {
		final int itemCount = Item.ALL.use(ds).count();
		final int itemCount2 = Item.ALL.use(ds).with(Item.FK_SUPPLIER).count();
		// counts should be the same w/ and w/o the FK reference
		assertTrue(itemCount == itemCount2);
		int count = 0;
		boolean sawEST29 = false;
		for (final Item item : Item.ALL.use(ccds).with(Item.FK_SUPPLIER)) {
			count++;
			if (!"EST-29".equals(item.getItemid()) && !item.getItemid().startsWith("test-")) {
				assertNotNull(item.getSupplierFK());
			} else {
				sawEST29 = true;
			}
		}
		assertTrue(sawEST29);
		assertEquals(itemCount, count);
		// make sure the calls to the FK object didn't gen new queries
		assertEquals(1, ccds.getCount());
	}

	public void testFKNoWith() throws SQLException {
		final Undoer x = Context.getVMContext().setDataSource(ccds);
		for (final Item item : Item.ALL) { //.with(Item.FK_SUPPLIER)
			// this should create O(n) queries because we didn't specify with() above
			item.getSupplierFK();
		}
		assertTrue(ccds.getCount() > 1);
	}

	@SuppressWarnings("unused")
	public void testFKReverseCounts() throws SQLException {
		System.err.println("testFKReverseCounts start");
		final Undoer x = Context.getVMContext().setDataSource(ds);
		final int count1 = Supplier.ALL.count();
		final int count2 = Supplier.ALL.with(Item.FK_SUPPLIER).count();
		int count3 = 0;
		int supplierCount = 0;
		for (final Supplier s : Supplier.ALL) {
			supplierCount += 1;
			final int itemCount = s.getItemSet().count();
			count3 += Math.max(1, itemCount);
		}
		assertEquals(count2, count3);
		assertEquals(count1, supplierCount);
	}

	@SuppressWarnings("unused")
	public void testFKReverse() throws SQLException {
		System.err.println("testFKReverse start");
		final int count1 = Supplier.ALL.count();
		int count2 = 0;
		final Undoer y = Context.getVMContext().setDataSource(ccds);
		for (final Supplier s : Supplier.ALL.with(Item.FK_SUPPLIER)) {
			count2++;
			System.err.println(s);
			for (final Item i : s.getItemSet()) {
				System.err.println("\t"+i);
			}
			if (s.getSuppid() == 1) assertTrue(s.getItemSet().count() > 0);
			else assertEquals(0, s.getItemSet().count());
		}
		assertEquals(count1, count2);
		assertEquals(1, ccds.getCount());
		System.err.println("testFKReverse done");
	}

	@SuppressWarnings("unused")
	public void testFKReverseQueryReplicationBug() throws SQLException {
		final Query<Supplier> q = Supplier.ALL.with(Item.FK_SUPPLIER);
		q.count();
		for (final Supplier s : q) {
		}
	}

	public void testFKTwoLevels() throws SQLException {
		final Undoer u = Context.getVMContext().setDataSource(ccds);
		for (final Item i : Item.ALL.with(Item.FK_PRODUCTID_PRODUCT, Product.FK_CATEGORY)) {
			assertNotNull(i);
			assertNotNull(i.getProductidFK());
			assertNotNull(i.getProductidFK().getCategoryFK());
			System.err.println(i);
		}
		assertEquals(1, ccds.getCount());
	}

	public void testSimpleDiff() throws SQLException {
		final List<Item> items = Item.ALL.asList();
		assertTrue(items.size() > 0);
		items.get(0).setAttr4("something");
		items.add(new Item());
		items.add(new Item());
		int updates = 0;
		int adds = 0;
		int count = 0;
		for (final RowChange<Item> diff : Diff.diff(items)) {
			count += 1;
			if (diff.isAdd()) adds += 1;
			if (diff.isUpdate()) updates += 1;
		}
		assertEquals(3, count);
		assertEquals(1, updates);
		assertEquals(2, adds);
	}

	public void testDateAdd() throws SQLException {
		Orderstatus.ALL.where(Orderstatus.TIMESTAMP.lt(
				DATEADD(Orderstatus.TIMESTAMP, 1, CALENDAR.DAY)))
				.asList();
	}

	public void testRecommendation() throws InterruptedException {
		for (final Item i : Item.ALL) {
			i.getProductidFK();
		}
		// note: this should raise a logging WARNING!
		System.gc();
		Thread.sleep(1000);
		// this doesn't work for now since it's the last test
	}

	public void testExists() throws InterruptedException {
		for (final Item i : Item.ALL.where(Item.ALL.exists())) {}
	}

	public void testConcat() throws SQLException {
		assertEquals(1, Item.ALL.where(CONCAT(Item.ITEMID, "!").eq("EST-20!")).count());
	}

	public void testAdd() throws SQLException {
		assertEquals(1, Inventory.ALL.where(Inventory.ITEMID.eq("EST-14"))
				.where(Inventory.QTY.add(0).eq(Inventory.QTY)).count());
	}

	public void testObjectArray() throws SQLException {
		final int count = Item.ALL.count();
		int count2 = 0;
		for (final Object[] oa : Item.ALL.asIterableOfObjectArrays()) {
			count2 += 1;
		}
		assertEquals(count, count2);
	}

    public void testTransaction() throws SQLException {
    	Context.getThreadContext().startTransaction(ds);
    	Item.ALL.get(Item.ITEMID.eq("EST-20"));
    	Context.getThreadContext().commitTransaction(ds);
    	Item.ALL.get(Item.ITEMID.eq("EST-20"));
    }

    public void testTopWithToManyRelationship() throws SQLException {
    	final Product p = Product.ALL.with(Item.FK_PRODUCTID_PRODUCT)
    			.where(Product.PRODUCTID.eq("FL-DSH-01"))
    			.first();
    	assertEquals(2, p.getItemSet().count());
    	assertEquals(1, Product.ALL.with(Item.FK_PRODUCTID_PRODUCT).top(1).asList().size());
    }

    public void testBulkInsert() throws SQLException {
    	final Query<Category> them = Category.ALL.where(Category.CATID.like("test-%"));
		them.deleteAll();
    	final List<Category> categories = new ArrayList<Category>();
    	categories.add(new Category().setCatid("test-1"));
    	categories.add(new Category().setCatid("test-2").setName("woot"));
    	categories.add(new Category().setCatid("test-3").setName("woot2"));
    	final Bulk bulk = new Bulk(ds);
    	bulk.insertAll(categories);
    	assertEquals(3, them.count());
    }

    public void testBulkUpdate() throws SQLException {
    	final int count = Item.ALL.count();
    	final List<Item> items = Item.ALL.asList();
		for (final Item item : items) {
    		item.setAttr2("woot2");
    		if (Math.random() > .5) {
    			item.setAttr3("woot3");
    		}
    		if (Math.random() > .5) {
    			item.setAttr4("woot4");
    		}
    	}
    	final Bulk bulk = new Bulk(ds);
    	final long ret = bulk.updateAll(items);
    	assertEquals(count, ret);
    }

    public void testBulkInsertOrUpdate() throws SQLException {
    	System.err.println("testBulkInsertOrUpdate");
    	Item.ALL.where(Item.ITEMID.like("test-%")).deleteAll();
    	final List<Item> updates = Item.ALL.asList();
    	String pid = null;
    	Integer sid = -1;
		for (final Item item : updates) {
			pid = item.getProductid();
			sid = item.getSupplier();
    		item.setAttr2("woot2");
    		if (Math.random() > .5) {
    			item.setAttr3("woot3");
    		}
    		if (Math.random() > .5) {
    			item.setAttr4("woot4");
    		}
    	}
		final List<Item> adds = new ArrayList<Item>();
		adds.add(new Item().setItemid("test-10").setProductid(pid).setSupplier(sid));
		adds.add(new Item().setItemid("test-20").setProductid(pid).setSupplier(sid).setAttr5("woot5"));
		final List<Item> all = new ArrayList<Item>();
		all.addAll(adds);
		all.addAll(updates);
    	final Bulk bulk = new Bulk(ds);
    	final long ret = bulk.insertOrUpdateAll(all);
    	assertEquals(all.size(), ret);
    	assertEquals(2, adds.size());
    	System.err.println(Item.ALL.get(Item.ITEMID.eq("test-10")));
    	System.err.println(Item.ALL.get(Item.ITEMID.eq("test-20")));
    	assertEquals(adds.size(), bulk.deleteAll(adds));
    }

    public void testBulkCommitDiff() throws SQLException {
    	System.err.println("testBulkCommitDiff");
    	final Product p = Product.ALL.first();
    	Item.ALL.where(Item.ITEMID.like("test-%")).deleteAll();
    	new Item().setItemid("test-1").setProductid(p.getProductid()).insert();
    	new Item().setItemid("test-2").setProductid(p.getProductid()).insert();
    	new Item().setItemid("test-3").setProductid(p.getProductid()).insert();
    	final List<Item> pre = Item.ALL.where(Item.ITEMID.like("test-%")).orderBy(Item.ITEMID).asList();
    	final List<Item> post = Item.ALL.where(Item.ITEMID.like("test-%")).orderBy(Item.ITEMID).asList();
    	post.get(0).setAttr2("woot2");
    	post.remove(1);
    	post.add(new Item().setItemid("test-4").setProductid(p.getProductid()));
    	final List<RowChange<Item>> diff = Diff.diffActualized(pre, post);
    	assertEquals(3, diff.size());
    	final Bulk bulk = new Bulk(ds);
    	final long ret = bulk.commitDiff(diff);
    	assertEquals(3, ret);
    	Item.ALL.where(Item.ITEMID.like("test-%")).deleteAll();
    }

    public void testCallbacks() throws SQLException {
    	final Item item = Item.ALL.first();
    	ItemCB.preUpdate = 0;
    	ItemCB.postUpdate = 0;
    	item.setAttr3("woot3");
    	item.update();
    	assertEquals(1, ItemCB.preUpdate);
    	assertEquals(1, ItemCB.postUpdate);
    }
    
    public void testCallbacksBulk() throws SQLException {
    	System.err.println("testCallbacksBulk");
    	final Item item = Item.ALL.first();
    	item.setAttr3("woot3");
    	ItemCB.preUpdate = 0;
    	ItemCB.postUpdate = 0;
    	final Bulk bulk = new Bulk(ds);
    	final List<Item> items = new ArrayList();
    	items.add(item);
    	bulk.updateAll(items);
    	assertEquals(1, ItemCB.preUpdate);
    	assertEquals(1, ItemCB.postUpdate);
    }
    
}