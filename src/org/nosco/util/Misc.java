package org.nosco.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;

import org.nosco.json.JSONException;
import org.nosco.json.JSONObject;

public class Misc {

	public static String join(String s, Collection<?> c) {
	    StringBuilder sb = new StringBuilder();
	    for (Object o : c) {
	    	sb.append(o);
	    	sb.append(s);
	    }
	    return sb.delete(sb.length()-s.length(), sb.length()).toString();
	}

	public static String join(String s, Object... c) {
		if(c==null || c.length==0) return "";
	    StringBuilder sb = new StringBuilder();
	    for (Object o : c) {
	    	sb.append(o);
	    	sb.append(s);
	    }
	    return sb.delete(sb.length()-s.length(), sb.length()).toString();
	}

	public static JSONObject loadJSONObject(String fn) throws IOException, JSONException {
		BufferedReader br = new BufferedReader(new FileReader(fn));
		StringBuffer sb = new StringBuffer();
		String s = null;
		while ((s=br.readLine())!=null) sb.append(s).append('\n');
		JSONObject o = new JSONObject(sb.toString());
		return o;
	}

}