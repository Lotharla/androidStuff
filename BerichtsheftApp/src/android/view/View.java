package android.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import android.content.Context;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;

import static com.applang.Util.*;

public class View
{
	public static int uniqueCounter = 0;
	
    public static String getUniquifiedName(String name) {
    	return name + (++uniqueCounter);
    }
    
	protected String name;

	public String getName() {
		return name;
	}
	
	public View(Component component) {
		this.component = component;
	}
	
	private Component component = null;

	public Component getComponent() {
		return component;
	}

	public void setComponent(Component component) {
		this.component = component;
	}
	
	private int mId;
	
	public int getId() {
		return mId;
	}
	
	public View setId(int id) {
		mId = id;
		return this;
	}

    public View findViewById(int id) {
        if (id < 0) {
            return null;
        }
        return findViewTraversal(id);
    }

    protected View findViewTraversal(int id) {
        if (id == mId) {
            return this;
        }
        return null;
    }

	public View(Context context, Object...params) {
		mContext = context;
		name = getUniquifiedName(param_String("view", 0, params));
		setId(0);
		create(params);
		if (component != null)
			component.setName(name);
    }
	
	protected String inputType;

    protected void create(Object... params) {
    	inputType = param_String("", 1, params);
	}

	private Context mContext = null;

	public Context getContext() {
		return mContext;
	}

	private LayoutParams mLayoutParams;
	
    public LayoutParams getLayoutParams() {
        return mLayoutParams;
    }
    
	public void setLayoutParams(LayoutParams params) {
		mLayoutParams = params;
		if (component != null) {
			Dimension size = component.getPreferredSize();
			if (mLayoutParams instanceof MarginLayoutParams) {
				MarginLayoutParams margs = (MarginLayoutParams) mLayoutParams;
				Dimension dim = new Dimension(params.width, params.height);
				if (dim.width > -1) {
					dim.width -= margs.leftMargin + margs.rightMargin;
				}
				if (dim.height > -1) {
					dim.height -= margs.topMargin + margs.bottomMargin;
				}
				if (dim.width > -1) 
					size.width = dim.width;
				if (dim.height > -1) 
					size.height = dim.height;
			}
			component.setPreferredSize(size);
		}
	}

	public void setPadding (int left, int top, int right, int bottom) {
		if (component != null && component instanceof JComponent) {
		    Border padding = new EmptyBorder(top, left, bottom, right);
		    JComponent jc = (JComponent) component;
		    Border border = jc.getBorder();
		    jc.setBorder(new CompoundBorder(border, padding));
		}
	}
	
	public void setBackgroundColor(int color) {
		getComponent().setBackground(new Color(color));
	}

}
