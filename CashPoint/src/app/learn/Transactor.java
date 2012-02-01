package app.learn;

import java.util.*;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

public class Transactor extends DbAdapter
{
	public static final String TAG = "Transactor";
	
	public Transactor(Context context) {
		super(context);
	}
    
    private double minAmount = 0.01;
    
    private boolean hasNegativeTotalWith(double amount) {
    	double total = total();
		if (total - amount < -minAmount) {
			Log.w(TAG, String.format("retrieval of %f is more than than allowed : %f", amount, total));
			return true;
		}
		else
			return false;
    }
    
    private boolean isInternal(String name) {
    	return name.length() < 1;
    }
	/**
	 * The transaction registers the negated sum of the shares with the submitter.
	 * Additionally all the shares in the map are registered with the same entry id.
	 * This transaction does not change the total. It registers an allocation of a certain amount.
	 * If submitter is an empty <code>String</code> it means there is an internal reallocation which is not an expense.
	 * Such an internal reallocation is not allowed if the transaction causes a negative total.
	 * @param submitter	the name of the participant who expended an amount matching the negated sum of the shares
	 * @param comment	a <code>String</code> to make the transaction recognizable
	 * @param shares	a <code>ShareMap</code> containing the shares of participants involved
	 * @return	the entry id of the transaction or -1 in case the transaction failed
	 */
	public int performExpense(String submitter, String comment, ShareMap shares) {
		double amount = -shares.sum();
		
		boolean internal = isInternal(submitter);
		if (internal) {
			if (hasNegativeTotalWith(-amount)) 
				return -2;
		}
		
		int entryId = addEntry(submitter, amount, currency, comment, !internal);
		if (entryId < 0)
			return -1;
		
    	if (allocate(!internal, entryId, shares)) {
			String action = internal ? "reallocation of" : submitter + " expended";
    		Log.i(TAG, String.format("entry %d: %s %f %s for '%s' shared by %s", 
    				entryId, action, Math.abs(amount), currency, comment, shares.toString()));
    	}
    	else {
    		removeEntry(entryId);
    		entryId = -1;
    	}
    	
		return entryId;
	}
    /**
	 * The transaction registers the negated sum of the shares as an expense by a group of contributors.
	 * Additionally all the shares in the map are registered with the same entry id.
	 * Those names in the deals map that are empty <code>String</code> trigger transfers of internal amounts.
	 * Such an internal transfer is not allowed if the transaction causes a negative total.
     * @param deals	the names of the contributors and their contributions who expended an amount matching the negated sum of the deals
	 * @param comment	a <code>String</code> to make the transaction recognizable
	 * @param shares	a <code>ShareMap</code> containing the shares of participants involved
	 * @return	the entry id of the transaction or a negative value in case the transaction failed
     */
	public int performComplexExpense(ShareMap deals, String comment, ShareMap shares) {
    	int entryId = -1;
    	
    	double amount = deals.sum();
    	if (Math.abs(amount + shares.sum()) < Util.delta)
	    	for (Map.Entry<String, Number> deal : deals.entrySet()) {
				String name = deal.getKey();
				
				if (isInternal(name)) 
					entryId = performTransfer(name, -deal.getValue().doubleValue(), comment, name);
				else {
					long rowId = addRecord(entryId < 0 ? getNewEntryId() : entryId, 
							name, deal.getValue().doubleValue(), currency, timestampNow(), comment);
					if (rowId < 0)
			    		entryId = -1;
					else
						updateExpenseFlag(rowId, true);
				}
				
		    	if (entryId < 0)
		    		break;
			}
    	else
    		Log.w(TAG, String.format("the sum of the deals (%f) for '%s' doesn't match the sum of the shares (%f)", 
    				amount, comment, shares.sum()));
    	
    	if (entryId > -1) {
        	if (allocate(true, entryId, shares)) {
        		Log.i(TAG, String.format("entry %d: %s expended %f %s for '%s' shared by %s", 
        				entryId, deals.toString(), Math.abs(amount), currency, comment, shares.toString()));
        	}
        	else {
        		removeEntry(entryId);
        		entryId = -1;
        	}
    	}
  	
    	if (entryId < 0)
    		removeEntry(entryId);
    	
		return entryId;
    }
	/**
	 * The transaction performs a transfer of the amount from a submitter to a recipient.
	 * This is the same as performing a contribution of the submitter and then a payout to the recipient.
	 * @param sender	the name of the participant who lost the amount
	 * @param amount	the amount of the transfer
	 * @param comment	a <code>String</code> to make the transaction recognizable
	 * @param recipient	the name of the participant who got the amount
	 * @return	the entry id of the transaction or a negative value in case the transaction failed
	 */
	public int performTransfer(String sender, double amount, String comment, String recipient) {
		boolean internal = isInternal(sender);
		if (internal) {
			if (hasNegativeTotalWith(amount)) 
				return -3;
		}
		
		int entryId = addEntry(sender, amount, currency, comment, false);
		if (entryId < 0)
			return -1;
		
		long rowId = addRecord(entryId, recipient, -amount, currency, timestampNow(), comment);
		if (rowId < 0){
    		removeEntry(entryId);
			return -2;
		}
		if (sender.equals(recipient)) 
			updateExpenseFlag(rowId, true);

		Log.i(TAG, String.format("entry %d: %s transfer %f %s for '%s' to %s", entryId, 
				internal ? "internal" : sender, 
				amount, currency, comment, 
				isInternal(recipient) ? "internal" : recipient));
		
		return entryId;
	}
	/**
	 * The transaction registers the amount as a contribution (positive) or a retrieval (negative).
	 * A retrieval is not allowed if it surmounts the current total.
	 * @param submitter	the name of the participant who did the submission
	 * @param amount	the amount of the submission
	 * @param comment	a <code>String</code> to make the transaction recognizable
	 * @return	the entry id of the transaction or -1 in case the transaction failed or -2 if the transaction violates the 'no negative total' rule
	 */
	public int performSubmission(String submitter, double amount, String comment) {
		int entryId = addEntry(submitter, amount, currency, comment, false);
		
		if (entryId > -1) {
			if (hasNegativeTotalWith(-amount)) {
	    		removeEntry(entryId);
				return -2;
			}
			
			String action = amount > 0 ? "contributed" : "retrieved";
			Log.i(TAG, String.format("entry %d: %s %s %f %s as '%s'", 
					entryId, submitter, action, Math.abs(amount), currency, comment));
		}
		
		return entryId;
	}
	/**
	 * The transaction registers multiple submissions.
	 * This transaction as a whole is not allowed if it causes a negative total.
	 * @param shares	a <code>ShareMap</code> containing the shares of participants involved
	 * @param comment	a <code>String</code> to make the transactions recognizable
	 * @return	the entry id of the transaction or -1 in case the transaction failed or -2 if the transactions in the sum violate the 'no negative total' rule
	 */
	public int performMultiple(ShareMap shares, String comment) {
		if (hasNegativeTotalWith(-shares.sum())) 
			return -2;
		
    	int entryId = getNewEntryId();
    	
		if (entryId > -1) {
			for (Map.Entry<String, Number> share : shares.entrySet())
				if (addRecord(entryId, share.getKey(), share.getValue().doubleValue(), currency, timestampNow(), comment) < 0) {
		    		removeEntry(entryId);
					return -1;
				}
			
			Log.i(TAG, String.format("entry %d: '%s' submissions : %s",
					entryId, comment, shares.toString()));
		}
		
		return entryId;
	}
	/**
	 * The transaction discards any record with that entry id.
	 * @param entryId	the entry to be discarded
	 * @return	the number of affected records
	 */
    public int performDiscard(int entryId) {
		int affected = removeEntry(entryId);
		Log.i(TAG, String.format("entry %d: discarded, %d records deleted", entryId, affected));
		return affected;
    }
    
    /**
     * calculates the balances of all participants identified by their names
	 * @return	a map containing the names as keys and the balances as values
     */
    public ShareMap balances() {
		return rawQuery("select name, sum(amount) as balance from " + DATABASE_TABLE + 
				" where length(name) > 0 group by name order by name", null, 
			new QueryEvaluator<ShareMap>() {
				public ShareMap evaluate(Cursor cursor, ShareMap defaultResult, Object... params) {
					ShareMap map = new ShareMap();
					
		    		do {
		    			map.put(cursor.getString(0), cursor.getDouble(1));
		    		} while (cursor.moveToNext());
		    		
		        	Log.i(TAG, String.format("balances : %s", map.toString()));
					return map;
				}
			}, null);
    }
    /**
     * calculates the amounted 'value' of the table
     * @return	the sum over the amounts of all records in the table
     */
    public double total() {
    	return getSum(null);
    }
    /**
     * calculates the accumulated costs
     * @return	the sum over all entries marked as 'expense'
     */
    public double expenses() {
    	return getSum("expense > 0 and timestamp not null");
    }
    /**
     * retrieves the names of tables that had been 'saved' in the past
     * @return	the <code>Set</code> of saved table names
     */
    public Set<String> savedTables() {
		return rawQuery("select name from sqlite_master where type = 'table'", null, 
			new QueryEvaluator<Set<String>>() {
				public Set<String> evaluate(Cursor cursor, Set<String> defaultResult, Object... params) {
			    	TreeSet<String> names = new TreeSet<String>();
		    		
			    	do {
		        		String name = cursor.getString(0);
		        		if (name.startsWith(DATABASE_TABLE + "_"))
		        			names.add(name);
		    		} while (cursor.moveToNext());
					
			    	Log.i(TAG, String.format("saved tables : %s", names.toString()));
			    	return names;
				}
			}, null);
    }
    /**
     * 
     * @param suffix	an arbitrary <code>String</code> which is legal as an SQLite table name
     * @return	the complete SQLite table name
     */
    public String tableName(String suffix) {
    	return DbAdapter.DATABASE_TABLE + "_" + suffix;
    }
    /**
     * changes the name of the table that has been worked on via transactions (current table). 
     * Thus this table is 'saved' in the same database. Note that the current table is non-existing after this operation. 
     * In order to continue the current table has to be restored (loadFrom) or recreated (clear).
     * @param newSuffix	the <code>String</code> to append to the name of the current table in order to form the new table name 
     * @return	success if true
     */
    public boolean saveAs(String newSuffix) {
    	if (newSuffix == null || newSuffix.length() < 1)
    		return false;
    	
    	String newTableName = tableName(newSuffix);
    	if (savedTables().contains(newTableName))
    		return false;
    		
    	rename(DATABASE_TABLE, newTableName);
		Log.i(TAG, String.format("table saved as '%s'", newTableName));
    	return true;
    }
    /**
     * restores one of the saved tables as the current table. Note that the table that was current up to this point will be dropped.
     * Also there will be one less 'saved' table after this operation.
     * @param oldSuffix	the <code>String</code> to append to the name of the current table in order to form the old table name 
     * @return	success if true
     */
    public boolean loadFrom(String oldSuffix) {
    	if (oldSuffix == null || oldSuffix.length() < 1)
    		return false;
    	
    	String oldTableName = tableName(oldSuffix);
    	if (!savedTables().contains(oldTableName))
    		return false;
    		
    	rename(oldTableName, DATABASE_TABLE);
		Log.i(TAG, String.format("table loaded from '%s'", oldTableName));
    	return true;
    }
    /**
     * deletes all records from the current table and recreates it
     */
    @Override
    public void clear() {
    	int count = getCount(null);
    	super.clear();
		Log.i(TAG, String.format("table cleared, %d records deleted", count));
    }
    /**
     * clears the current table and drops all saved tables
     */
    public void clearAll() {
    	for (String table : savedTables())
    		drop(table);
    	
    	super.clear();
		Log.i(TAG, "table and all saved tables cleared");
    }
	
	private String currency = "";
	
	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}
    
}
