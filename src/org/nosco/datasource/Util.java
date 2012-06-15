package org.nosco.datasource;

import java.io.PrintStream;
import java.util.Collection;
import java.util.List;

import org.nosco.Constants;

class Util {

	static String join(String s, Collection<?> c) {
	    StringBuilder sb = new StringBuilder();
	    for (Object o : c) {
	    	sb.append(o);
	    	sb.append(s);
	    }
	    if (c != null && c.size() > 0) {
	    	sb.delete(sb.length()-s.length(), sb.length());
	    }
	    return sb.toString();
	}

	static <T extends Object> String join(String s, T... c) {
		if(c==null || c.length==0) return "";
	    StringBuilder sb = new StringBuilder();
	    for (Object o : c) {
	    	sb.append(o==null ? "" : o.toString());
	    	sb.append(s);
	    }
	    return sb.delete(sb.length()-s.length(), sb.length()).toString();
	}

	static void log(String sql, List<Object> bindings) {
		PrintStream log = null; //System.out;
		String property = System.getProperty(Constants.PROP_LOG_SQL);
		String property2 = System.getProperty(Constants.PROP_LOG);
		if ("System.err".equalsIgnoreCase(property))
			log = System.err;
		if ("System.out".equalsIgnoreCase(property))
			log = System.out;
		if (log == null && truthy(property))
			log = System.err;
		if ("System.err".equalsIgnoreCase(property2))
			log = System.err;
		if ("System.out".equalsIgnoreCase(property2))
			log = System.out;
		if (log == null && truthy(property2))
			log = System.err;
		if (log == null) return;
		log.println("==> "+ sql +"");
		if (bindings != null && bindings.size() > 0)
			log.println("^^^ ["+ join("|", bindings) +"]");
	}

	static boolean truthy(String s) {
		if (s == null) return false;
		s = s.trim();
		if ("true".equalsIgnoreCase(s)) return true;
		if ("t".equalsIgnoreCase(s)) return true;
		if ("1".equals(s)) return true;
		return false;
	}

}
