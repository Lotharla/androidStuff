package com.applang.components;

import java.awt.Container;
import java.awt.Image;

import javax.swing.JComponent;

import org.w3c.dom.Document;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import static com.applang.Util.*;
import static com.applang.Util2.*;

public class DataForm
{
	private static final String TAG = DataForm.class.getSimpleName();
	
	public DataForm(Context context, ManagerBase<?> manager, BidiMultiMap projection, Object...resource) {
		mManager = manager;
		boolean standard = notAvailable(0, resource);
		if (standard) 
			resource = objects("standard_form.xml");
		builder = new Builder(context, resource[0]);
		if (projection != null) {
			mProjection = new BidiMultiMap(
					projection.getValues(0), 
					projection.getValues(1), 
					projection.getValues(2), 
					vlist(), 
					projection.getValues(4));
			for (Object key : mProjection.getKeys()) {
				String name = stringValueOf(key);
				String type = stringValueOf(mProjection.getValue(key, 2));
				String style = stringValueOf(mProjection.getValue(key, 4));
				if (nullOrEmpty(style))
					style = type.toLowerCase();
				View view = standard ? 
						builder.addStandardField(key, name, style) : 
						viewGroup.findViewWithTag(name);
				mProjection.putValue(key, view, 3);
			}
		}
		else
			mProjection = null;
	}

	public Builder builder;
	
	public ViewGroup viewGroup = null;
    
    public Container getContainer() {
    	ViewGroup.build(viewGroup, true);
    	return viewGroup.getContainer();
    }
    
	public BidiMultiMap mProjection;

    private int fieldType(Object key) {
    	return fieldTypeAffinity(stringValueOf(mProjection.getValue(key, 2)));
    }
	
	private Object doConversion(Object key, Object value, String oper) {
		Object conversion = mProjection.getValue(key, 1);
		if (notNullOrEmpty(conversion))
			return ScriptManager.doConversion(value, stringValueOf(conversion), oper);
		else
			return value;
	}

	public Object[] getContent() {
		ValList list = vlist();
		ValList keys = mProjection.getKeys();
		for (int i = 0; i < keys.size(); i++) 
			list.add(getContent(keys.get(i)));
		return list.toArray();
	}

	private Object getContent(Object key) {
		Object value = null;
		View view = mProjection.getValue(key, 3);
		if (view != null) {
			switch (fieldType(key)) {
			case Cursor.FIELD_TYPE_BLOB:
				value = ((ImageView)view).getImage();
				break;
			default:
				if (view instanceof TextEdit) {
					TextEdit textEdit = (TextEdit)view;
					if (textEdit.getTextToggle() != null)
						value = textEdit.getScript();
					else
						value = textEdit.getText();
				}
				else
					value = ((EditText)view).getText();
			}
		}
		return doConversion(key, value, "pull");
    }

	public void setContent(final Object[] values) {
		mManager.blockDirty(new Job<Void>() {
			public void perform(Void t, Object[] parms) throws Exception {
				ValList keys = mProjection.getKeys();
				for (int i = 0; i < keys.size(); i++) {
					Object key = keys.get(i);
					setContent(key, values[i]);
				}
			}
		});
    }

	private void setContent(final Object key, Object value) {
		final View view = mProjection.getValue(key, 3);
		if (view != null) {
			final Object o = doConversion(key, value, "push");
			switch (fieldType(key)) {
			case Cursor.FIELD_TYPE_BLOB:
				((ImageView)view).setImage((Image) o);
				break;
			default:
				String text = stringValueOf(o);
				if (view instanceof TextEdit) {
					TextEdit textEdit = (TextEdit)view;
					if (textEdit.getTextToggle() != null) {
						textEdit.setScript(text);
						textEdit.setText(textEdit.getScript());
					}
					else
						textEdit.setText(text);
				}
				else
					((EditText)view).setText(text);
			}
		}
	}
	
	private ManagerBase<?> mManager;
	
	private Job<JComponent> onChanged = new Job<JComponent>() {
		public void perform(JComponent t, Object[] params) throws Exception {
			mManager.setDirty(true);
		}
	};
	
	public class Builder
	{
		protected LayoutInflater inflater = null;

		public Builder(Context context, Object resource) {
			inflater = LayoutInflater.from(context);
			if (nullOrEmpty(resource)) {
				viewGroup = new ViewGroup(context);
			}
			else if (resource instanceof Integer) {
				Document document = context.getResources().getXml((Integer) resource);
				viewGroup = (ViewGroup) inflater.inflate(document.getDocumentElement());
			}
			else {
				View view = inflater.inflate(templatePath(stringValueOf(resource)));
				if (view instanceof ViewGroup)
					viewGroup = (ViewGroup) view;
				else {
					viewGroup = new ViewGroup(context);
					addView(view, view.getLayoutParams());
				}
			}
			viewGroup.setTag("form");
		}

		protected String templatePath(String name) {
			return Resources.getRelativePath(6, name);
		}

		public void setLabel(Object labelText, ViewGroup vg) {
			TextView textView = (TextView) vg.getChildAt(0);
			textView.setText(stringValueOf(labelText));
		}
		
		public View getEdit(ViewGroup vg) {
			return vg.getChildAt(1);
		}
		
	    public void addView(View view, ViewGroup.LayoutParams params) {
	    	viewGroup.addView(view, params);
		}

		public View addStandardField(Object description, String name, String style) {
			ViewGroup vg = (ViewGroup) inflater.inflate(
					templatePath("standard_field.xml"), 
					name, 
					style);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			View vw = getEdit(vg);
			if (vw instanceof EditText) {
				((EditText) vw).setOnTextChanged(onChanged);
				if (vw instanceof TextEdit) {
					TextEdit te = (TextEdit) vw;
					if (te.getTextToggle() != null)
						te.getTextToggle().setOnTextChanged(onChanged);
				}
			}
			return vg.findViewWithTag(name);
		}

		public View addField(Object description, String key, String type) {
			ViewGroup vg = null;
			switch (fieldTypeAffinity(type)) {
			case Cursor.FIELD_TYPE_STRING:
				vg = addStringField(description, key);
				break;
			case Cursor.FIELD_TYPE_INTEGER:
				vg = addIntegerField(description, key);
				break;
			case Cursor.FIELD_TYPE_FLOAT:
				vg = addFloatField(description, key);
				break;
			case Cursor.FIELD_TYPE_BLOB:
				vg = addBlobField(description, key);
				break;
			default:
				Log.w(TAG, String.format("type of field '%s' not identified : %s", key, type));
				break;
			}
			if (vg != null)
				return vg.findViewWithTag(key);
			else
				return null;
		}
	    
	    public ViewGroup addTextField(Object description, Object...params) {
	    	ViewGroup vg = (ViewGroup) inflater.inflate(templatePath("field_text.xml"), params);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			return vg;
		}

	    public ViewGroup addStringField(Object description, Object...params) {
	    	ViewGroup vg = (ViewGroup) inflater.inflate(templatePath("field_string.xml"), params);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			return vg;
		}

		public ViewGroup addIntegerField(Object description, Object...params) {
			ViewGroup vg = (ViewGroup) inflater.inflate(templatePath("field_integer.xml"), params);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			return vg;
		}

		public ViewGroup addFloatField(Object description, Object...params) {
			ViewGroup vg = (ViewGroup) inflater.inflate(templatePath("field_float.xml"), params);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			return vg;
		}

		public ViewGroup addBlobField(Object description, Object...params) {
			ViewGroup vg = (ViewGroup) inflater.inflate(templatePath("field_blob.xml"), params);
			addView(vg, vg.getLayoutParams());
			setLabel(description, vg);
			return vg;
		}

	}
}
