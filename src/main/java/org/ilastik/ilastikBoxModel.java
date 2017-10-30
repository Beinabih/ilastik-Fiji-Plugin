package org.ilastik;

import java.util.Vector;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.ListDataListener;


public class ilastikBoxModel implements MutableComboBoxModel {
	
	Vector eintraege = new Vector();
	   int index=-1;
	   

	    public Object getSelectedItem()
	    {
	        if(index >= 0)
	        {
	            return ((comboBoxDimensions)eintraege.elementAt(index)).getEintrag();
	        }
	        else
	        {
	            return "";
	        }
	    }
	 

	    public void setSelectedItem(Object anItem)
	    {
	        for(int i = 0; i< eintraege.size(); i++)
	        {
	            if(((comboBoxDimensions)eintraege.elementAt(i)).
	            		getEintrag().equals(anItem))
	            {
	                index = i;
	                break;
	            }
	        }
	    }
	 

	    public int getSize()
	    {
	        return eintraege.size();
	    }

	    
	    public Object getElementAt(int index)
	    {
	        return ((comboBoxDimensions)eintraege.elementAt(index)).getEintrag();
	    }
	 

	    public void addElement(Object obj)
	    {
	        if(!eintraege.contains(obj))
	        {
	            int i=0;
	 
	            while(i<eintraege.size() && ((comboBoxDimensions)obj).isValid() == "works") {
	                    i++;
	           }
	 
	            eintraege.add(i, obj);
	            if(index==-1)index=0;
	        }
	    }
	 

	    public void removeElement(Object obj)
	    {
	        if(eintraege.contains(obj))
	        {
	            eintraege.remove(obj);
	        }
	    }
	 
	    
	    public void insertElementAt(Object obj, int index)
	    {
	        eintraege.add(index, obj);
	    }
	 

	    public void removeElementAt(int index)
	    {
	        if(eintraege.size()> index)
	        {
	            eintraege.removeElementAt(index);
	        }
	    }
	 

	    public void addListDataListener(ListDataListener l)
	    {
	    }
	 
	    public void removeListDataListener(ListDataListener l)
	    {
	    }
	

}
