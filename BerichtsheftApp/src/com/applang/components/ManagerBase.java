package com.applang.components;

import static com.applang.SwingUtil.*;
import static com.applang.Util.*;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Stack;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import com.applang.berichtsheft.plugin.BerichtsheftPlugin;

@SuppressWarnings("rawtypes")
public abstract class ManagerBase<T extends Object> extends JComponent
{
	public JComboBox[] comboBoxes;
	
	public JTextField comboEdit(int index) {
		return com.applang.SwingUtil.comboEdit(comboBoxes[index]);
	}
	
	public void comboChangeListener(final int index) {
		comboBoxes[index].addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent ev) {
				if (ev.getStateChange() == ItemEvent.DESELECTED) {
					String item = stringValueOf(ev.getItem());
					String old = comboEdit(index).getText();
					setDirty(!item.equals(old));
				}
			}
		});
	}
	
	public JLabel labelFor(Component c, String text, float alignmentX) {
		JLabel label = new JLabel(text);
		label.setName(text + "_Label");
		label.setAlignmentX(alignmentX);
		label.setLabelFor(c);
		return label;
	}
	
	protected String toString(Object item) {
		return String.valueOf(item);
	}

	public boolean isItemValid(Object item) {
		return notNullOrEmpty(item);
	}
	
	protected boolean saveThis(boolean exists, Object item) {
		if (!exists || isDirty()) {
			setDirty(false);
			if (isItemValid(item)) {
				String string = exists ? 
						"manager.save.message.1" : 
						"manager.save.message.2";
				string = BerichtsheftPlugin.getProperty(string);
				return question(String.format(string, toString(item)), 
						null,
						JOptionPane.YES_NO_OPTION);
			}
		}
		return false;
	}

	public Boolean save(Object item, boolean refresh) {
		boolean exists = select(item) != null;
		if (saveThis(exists, item)) {
			if (exists) {
				updateItem(true, item);
				BerichtsheftPlugin.consoleMessage("manager.update.message.1", toString(item));
			}
			else
				if (!addItem(refresh, item))
					BerichtsheftPlugin.consoleMessage("manager.add.message.1", toString(item));
				else
					BerichtsheftPlugin.consoleMessage("manager.add.message.2", toString(item));
			return exists;
		}
		return null;
	}
	
	protected boolean deleteThis(Object item) {
		String string = String.format(BerichtsheftPlugin.getProperty("manager.delete.message"), toString(item));
		if (isItemValid(item) && question(string, null, JOptionPane.YES_NO_OPTION)) {
			setDirty(false);
			return true;
		}
		return false;
	}

	public Boolean delete(Object item) {
		if (deleteThis(item)) {
			boolean done = removeItem(item);
			BerichtsheftPlugin.consoleMessage(
					done ? 
							"manager.remove.message.2" : 
							"manager.remove.message.1", 
					ManagerBase.this.toString(item));
			return done;
		}
		return null;
	}

	protected Object getItem() {
		return comboEdit(0).getText();
	}
	
	protected void installAddRemove(Container container, final String itemName) {
		container.add(BerichtsheftPlugin.makeCustomButton("manager.add", new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				save(getItem(), true);
			}
		}, false));
		container.add(BerichtsheftPlugin.makeCustomButton("manager.remove", new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				delete(getItem());
			}
		}, false));
	}
	
	private AbstractButton[] abuttons = new AbstractButton[2];
	
	protected void installUpdate(Container container) {
		container.add(abuttons[0] = BerichtsheftPlugin.makeCustomButton("manager.update-change", 
				new ActionListener() {
					public void actionPerformed(ActionEvent evt) {
						updateChange(true);
					}
				}, 
				false));
		container.add(abuttons[1] = BerichtsheftPlugin.makeCustomButton("manager.erase-change", 
				new ActionListener() {
					public void actionPerformed(ActionEvent evt) {
						updateChange(false);
					}
				}, 
				false));
		changePossible.push(true);
		setDirty(false);
		changePossible.pop();
	}

	public void updateChange(boolean update) {
		updateItem(update);
		BerichtsheftPlugin.consoleMessage("manager.update.message.1", toString(getItem()));
		setDirty(false);
	}

	private Stack<Boolean> changePossible;
	
	{
		changePossible = new Stack<Boolean>();
		changePossible.push(true);
	}
	
	public boolean isDirty() {
		if (changePossible.peek()) 
			return abuttons[0].isEnabled();
		else
			return false;
	}
	
	public void setDirty(boolean dirty) {
		if (changePossible.peek()) 
			for (AbstractButton button : abuttons) 
				button.setEnabled(dirty);
	}
	
	protected void blockChange(Job<Void> job, Object...params) {
		try {
			changePossible.push(false);
			job.perform(null, params);
		} catch (Exception e) {
			handleException(e);
		}
		finally {
			changePossible.pop();
		}
	}

	protected void updateText(final TextComponent textComponent, final String text) {
		blockChange(new Job<Void>() {
			public void perform(Void t, Object[] params) throws Exception {
				textComponent.setText(text);
			}
		});
		setDirty(false);
	}

	protected abstract T select(Object...args);
	
	protected abstract void updateItem(boolean update, Object...args);
	
	protected abstract boolean addItem(boolean refresh, Object item);
	
	protected abstract boolean removeItem(Object item);
}