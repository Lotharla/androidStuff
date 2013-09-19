package android.app;

import java.awt.Dimension;
import java.awt.Frame;

import javax.swing.JDialog;

import com.applang.SwingUtil;

import static com.applang.Util.*;
import static com.applang.SwingUtil.*;

public class Dialog extends JDialog
{
	public Dialog(Frame owner, boolean modal) {
		super(owner, modal);
	}

	public void dismiss() {
		setVisible(false);
	}

	public void open(Object...params) {
		Object param0 = param(null, 0, params);
		if (param0 instanceof Dimension)
			setSize((Dimension)param0);
		else if (param0 instanceof Double) {
			scaleDimension(this, arraycast(params, new Double[0]));
		}
		setVisible(true);
		toFront();
	}
}
