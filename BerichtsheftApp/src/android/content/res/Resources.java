package android.content.res;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.applang.Util.Constraint;
import com.applang.Util2;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import static com.applang.Util.*;
import static com.applang.Util2.*;
import static com.applang.SwingUtil.*;

public class Resources
{
	private static final String TAG = Resources.class.getSimpleName();
	
	private Context context;
	
	public Resources(Context context) {
		this.context = context;
	}
	
	public static String getSettingsPath() {
		return System.getProperty("settings.dir", "");
	}
	
	public static String getAbsolutePath(String part, Object...params) {
		Constraint constraint = param(Constraint.END, 0, params);
		part = strip(Constraint.START, part, PATH_SEP);
		File file = fileOf(getSettingsPath(), part);
		String path = findFirstFile(file.getParentFile(), constraint, file.getName());
		if (notNullOrEmpty(path))
			return path;
		else
			return absolutePathOf(part, constraint);
	}
	
	public static String[] resourceTypes = 
		{"array","attr","color","dimen","drawable","id","layout","menu","raw","string"};
	public static final Pattern XML_RESOURCE_PATTERN = 
		Pattern.compile("@([\\+]*)((([\\w]+\\.)*[\\w]+):)*(" + join("|", resourceTypes) + ")/([\\w\\.]+)");
	public static String[] resourceLocations = 
		{"res/values/strings.xml",	"attr",
		"res/values/colors.xml",
		"res/values/dimens.xml",				//	3
		"res/drawable/",	"id",
		"res/layout/",							//	6
		"res/menu/",
		"res/raw/",
		"res/values/strings.xml"};				//	9
	
	public static String getRelativePath(int loc, String...parts) {
		parts = arrayextend(parts, true, resourceLocations[loc]);
		String path = fileOf(parts).getPath();
		return PATH_SEP + strip(Constraint.START, path, PATH_SEP);
	}
	
	//	@[<package_name>:]<resource_type>/<resource_name>
	@SuppressWarnings("unchecked")
	public <T> T getXMLResourceItem(String attribute) {
		T value = null;
		if (notNullOrEmpty(attribute)) {
			try {
				MatchResult m = findFirstIn(attribute, XML_RESOURCE_PATTERN);
				if (m != null) {
					String pkg = m.group(3);
					String name = m.group(6), path;
					int type = asList(resourceTypes).indexOf(m.group(5));
					switch (type) {
					default:
						value = (T) name;
						break;
					case 4:
						if ("android".equals(pkg)) {
							value = (T) getDrawable (-1, pkg, name);
							if (value != null)
								break;
						}
						path = fileOf(resourceLocations[type], name).getPath();
						path = Resources.getAbsolutePath(path, Constraint.MIDDLE);
						if (notNullOrEmpty(path))
							value = (T) new Drawable().setImage(path);
						break;
					case 2:
					case 3:
					case 9:
						if ("android".equals(pkg)) {
							Job<Class<?>> job = new Job<Class<?>>() {
								@SuppressWarnings("unused")	//	used via reflect
								public String value;
								public void perform(Class<?> t, Object[] parms) throws Exception {
									int id = param_Integer(-1,2,parms);
									value = getString(id);
								}
							};
							if (lookup_R(-1, pkg, type, job, null, name)) {
								value = (T) job.getClass().getField("value").get(job);
								if (value != null)
									break;
							}
						}
						path = Resources.getAbsolutePath(resourceLocations[type], Constraint.END);
						if (notNullOrEmpty(path))
							value = getResourceByName(xmlDocument(new File(path)), name, type);
						break;
					}
					return value;
				}
			} catch (Exception e) {
				Log.e(TAG, "getXMLResourceItem", e);
			}
		}
		return value;
	}

	public static String textValue(Context context, String s) {
    	if (s.startsWith("@"))
    		return context.getResources().getXMLResourceItem(s);
    	else
    		return s;
	}

	public static int colorValue(Context context, String s) {
		Object o = context.getResources().getXMLResourceItem(s);
		if (o instanceof Integer)
			return (Integer) o;
		else {
			return android.graphics.Color.parseColor(s);
		}
	}

	public static int dimensionalValue(Context context, String s) {
		Object o = context.getResources().getXMLResourceItem(s);
		if (o instanceof Integer)
			return (Integer) o;
		else 
			return toInt(0, stripUnits(stringValueOf(s)));
	}

	public static boolean booleanValue(Context context, String s) {
		Object o = context.getResources().getXMLResourceItem(s);
		if (o instanceof String)
			return toBool(false, (String) o);
		else 
			return toBool(false, s);
	}
	
    //	NOTE	methods further up do NOT correspond to Android APIs
	
	public String getString(int id, Object...params) {
		String pkg = param(context.getPackageName(), 0, params);
		final int resourceType = 9;
		switch (id) {
		case android.R.string.close:
			return (String) defaultOptions(JOptionPane.DEFAULT_OPTION).get(0);
		case android.R.string.cancel:
			return (String) UIManager.get("OptionPane.cancelButtonText");
		case android.R.string.ok:
			return (String) UIManager.get("OptionPane.okButtonText");
		case android.R.string.yes:
			return (String) UIManager.get("OptionPane.yesButtonText");
		case android.R.string.no:
			return (String) UIManager.get("OptionPane.noButtonText");
		default:
			params = params.length < 1 ? objects(_null()): params;
			params[0] = null;
			lookup_R(id, pkg, resourceType, new Job<Class<?>>() {
				public void perform(Class<?> c, Object[] parms) throws Exception {
					final String name = param_String(null,0,parms);
					InputStream is = c.getResourceAsStream(getRelativePath(resourceType));
					Document doc = xmlDocument(null, is);
					Object[] params = param(null,1,parms);
					if (params != null)
						params[0] = getResourceByName(doc, name, resourceType);
				}
			}, params);
			if (null != params[0])
				return (String) params[0];
		}
		return "";
	}
	public int getDimensionPixelOffset(int id) {
		final int resourceType = 3;
		switch (id) {
		default:
			Object[] params = {0};
			lookup_R(id, context.getPackageName(), resourceType, new Job<Class<?>>() {
				public void perform(Class<?> c, Object[] parms) throws Exception {
					final String name = param_String(null,0,parms);
					InputStream is = c.getResourceAsStream(getRelativePath(resourceType));
					Document doc = xmlDocument(null, is);
					Object[] params = param(null,1,parms);
					if (params != null)
						params[0] = getResourceByName(doc, name, resourceType);
				}
			}, params);
			if (null != params[0])
				return (Integer) params[0];
		}
		return 0;
	}
	public Drawable getDrawable(int id, Object...params) {
		String pkg = param(context.getPackageName(), 0, params);
		final int resourceType = 4;
		switch (id) {
		default:
			params = params.length < 1 ? objects(_null()): params;
			params[0] = null;
			lookup_R(id, pkg, resourceType, new Job<Class<?>>() {
				public void perform(Class<?> c, Object[] parms) throws Exception {
					final String name = getRelativePath(resourceType, param_String(null,0,parms));
					InputStream is = getInputStreamByName(c, name, resourceType);
					Object[] params = param(null, 1, parms);
					if (params != null)
						params[0] = is;
				}
			}, params);
			if (null != params[0])
				return new Drawable().setImage((InputStream) params[0]);
		}
		return null;
	}
	public InputStream openRawResource(int id) {
		final int resourceType = 8;
		switch (id) {
		default:
			Object[] params = {null};
			lookup_R(id, context.getPackageName(), resourceType, new Job<Class<?>>() {
				public void perform(Class<?> c, Object[] parms) throws Exception {
					final String name = getRelativePath(resourceType, param_String(null,0,parms));
					InputStream is = getInputStreamByName(c, name, resourceType);
					Object[] params = param(null, 1, parms);
					if (params != null)
						params[0] = is;
				}
			}, params);
			if (null != params[0])
				return (InputStream) params[0];
		}
		return null;
	}
	
	public AssetManager getAssets() {
		return new AssetManager();
	}
	
    //	NOTE	methods further down do NOT correspond to Android APIs
	
	public Document getXml(int id) {
		final int resourceType = 6;
		switch (id) {
		default:
			Object[] params = {0};
			lookup_R(id, context.getPackageName(), resourceType, new Job<Class<?>>() {
				public void perform(Class<?> c, Object[] parms) throws Exception {
					final String name = getRelativePath(resourceType, param_String(null,0,parms));
					InputStream is = getInputStreamByName(c, name, resourceType);
					Object[] params = param(null,1,parms);
					if (params != null)
						params[0] = xmlDocument(null, is);
				}
			}, params);
			if (null != params[0])
				return (Document) params[0];
		}
		return null;
	}
	
	private boolean lookup_R(int id, String pkg, int resourceType, Job<Class<?>> lookup, Object...params) {
		try {
			Class<?> c = Class.forName(pkg + (notNullOrEmpty(pkg) ? ".R" : "R"));
			for (Class<?> inner : c.getDeclaredClasses()) {
				if (resourceTypes[resourceType].equals(inner.getSimpleName())) {
					String nameValue = param_String(null, 1, params);
					for (Field field : inner.getDeclaredFields()) {
						int idValue = field.getInt(null);
						if ("int".equals(field.getType().getSimpleName()) && 
								idValue == id ||
								field.getName().equals(nameValue))
						{
							if (lookup != null)
								lookup.perform(c, objects(field.getName(), params, idValue));
							return true;
						}
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "lookup_R", e);
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getResourceByName(Document doc, final String name, int type) {
		for (Element elem : iterateElements("*", doc, new Predicate<Element>() {
			public boolean apply(Element el) {
				return name.equals(el.getAttribute("name"));
			}})) 
		{
			String value = elem.getTextContent();
			switch (type) {
			case 2:
				return (T) new Integer(android.graphics.Color.parseColor(value));
			case 3:
				return (T) toInt(0, stripUnits(value));
			default:
				return (T) value;
			}
		}
		return null;
	}
	
	public InputStream getInputStreamByName(Class<?> c, final String name, int type) {
		Set<URL> res = getResourceURLs(
				new ResourceURLFilter() {
					public boolean accept(URL resourceUrl) {
						String url = resourceUrl.getFile();
						return check(url, Constraint.MIDDLE, name + DOT);
					}
				});
		if (res.iterator().hasNext()) {
			URL url = res.iterator().next();
			String path = url.getFile();
			path = path.substring(path.indexOf(name));
			return c.getResourceAsStream(path);
		}
		return null;
	}

	public interface ResourceURLFilter {
		public boolean accept(URL resourceUrl);
	}

	private static void collectURL(ResourceURLFilter f, Set<URL> s, URL u) {
		if (f == null || f.accept(u)) {
			s.add(u);
		}
	}

	private static void iterateFileSystem(File r, ResourceURLFilter f, Set<URL> s) throws MalformedURLException, IOException {
		File[] files = r.listFiles();
		for (File file: files)
			if (file.isDirectory())
				iterateFileSystem(file, f, s);
			else if (file.isFile())
				collectURL(f, s, file.toURI().toURL());
	}

	private static void iterateJarFile(File file, ResourceURLFilter f, Set<URL> s) throws MalformedURLException, IOException {
		JarFile jarfile = new JarFile(file);
		for(Enumeration<JarEntry> je = jarfile.entries(); je.hasMoreElements();) {
			JarEntry j = je.nextElement();
			if (!j.isDirectory()) {
				collectURL(f, s, new URL("jar", "", file.toURI() + "!/" + j.getName()));
			}
		}
		jarfile.close();
	}

	private static void iterateEntry(File p, ResourceURLFilter f, Set<URL> s) throws MalformedURLException, IOException {
		if (p.isDirectory()) {
			iterateFileSystem(p, f, s);
		} else if (p.isFile() && p.getName().toLowerCase().endsWith(".jar")) {
			iterateJarFile(p, f, s);
		}
	}

	public static Set<URL> getResourceURLs() {
		return getResourceURLs((ResourceURLFilter)null);
	}
	
	public static Set<URL> getResourceURLs(ResourceURLFilter filter) {
		Set<URL> collectedURLs = new HashSet<URL>();
		try {
			URLClassLoader ucl = (URLClassLoader)ClassLoader.getSystemClassLoader();
			for (URL url: ucl.getURLs()) {
				iterateEntry(new File(url.toURI()), filter, collectedURLs);
			}
		} catch (Exception e) {
			Log.e(TAG, "getResourceURLs", e);
		}
		return collectedURLs;
	}
	
	public static Set<URL> getResourceURLs(Class<?> rootClass) throws Exception {
		String packageName = rootClass.getName();
		packageName = packageName.substring(0, packageName.lastIndexOf("."));
		return getResourceURLs(packageName, (ResourceURLFilter)null);
	}
	
	public static Set<URL> getResourceURLs(String packageName, ResourceURLFilter filter) throws Exception {
		Set<URL> collectedURLs = new HashSet<URL>();
		URI location = getCodeSourceLocation(Resources.class);
//		location = location.resolve(packageName.replace('.', '/'));
		iterateEntry(new File(location), filter, collectedURLs);
		return collectedURLs;
	}
	
	public static URI getCodeSourceLocation(Class<?> rootClass) {
		try {
			CodeSource src = rootClass.getProtectionDomain().getCodeSource();
			return src.getLocation().toURI();
		} catch (Exception e) {
			Log.e(TAG, "getCodeSourceLocation", e);
			debug_println("code source not available");
			throw new RuntimeException("code source not available");
		}
	}
	
	public static String className(String resourceClassFileName, String sourceLocation) {
		return strip(Constraint.START, resourceClassFileName.substring(sourceLocation.length()), "!/").replace('/', '.');
	}
	
	public static void list(Writer writer, String packageName, ResourceURLFilter filter) {
		try {
			for (URL u: getResourceURLs(packageName, filter)) {
				writer.append(u + NEWLINE);
			}
		} catch (Exception e) {
			Log.e(TAG, "list", e);
		}
	}
}
