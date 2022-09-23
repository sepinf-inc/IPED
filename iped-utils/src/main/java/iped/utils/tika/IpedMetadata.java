package iped.utils.tika;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.tika.metadata.Metadata;

public class IpedMetadata extends Metadata{
    
	public void set(String arg0, ArrayList<String> list) {
		String[] arr= new String[list.size()];
		int i=0;
		for (Iterator iterator = list.iterator(); iterator.hasNext();) {
			arr[i++]=(String) iterator.next();
		}
		
		super.set(arg0, arr);
	}

}
