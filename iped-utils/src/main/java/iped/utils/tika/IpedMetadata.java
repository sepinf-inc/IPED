package iped.utils.tika;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.apache.tika.metadata.writefilter.MetadataWriteFilter;

public class IpedMetadata extends SyncMetadata {
    class IpedMetadataFilter implements MetadataWriteFilter {
        String arr[];
        int pos = -1;

        @Override
        public void add(String field, String value, Map<String, String[]> data) {
            if (pos == -1) {
                // when pos == -1 the method to add an array list was never called
                set(field, value, data);
            } else {
                arr[pos] = value;
                pos++;
                if (pos == arr.length) {
                    data.put(field, arr);
                }
            }
        }

        //legacy behavior -- remove the field if value is null
        @Override
        public void set(String field, String value, Map<String, String[]> data) {
            if (value != null) {
                data.put(field, new String[]{ value });
            } else {
                data.remove(field);
            }
        }

        @Override
        public void filterExisting(Map<String, String[]> arg0) {
            // TODO Auto-generated method stub

        }

        public void allocateSpace(int count) {
            arr = new String[count];
            pos = 0;
        }
    }

    IpedMetadataFilter ipedFilter = new IpedMetadataFilter();

    public IpedMetadata() {
        setMetadataWriteFilter(ipedFilter);
    }

	public void set(String arg0, ArrayList<String> list) {
        String[] arr = new String[list.size()];
		int i=0;
		for (Iterator iterator = list.iterator(); iterator.hasNext();) {
			arr[i++]=(String) iterator.next();
		}

        ipedFilter.allocateSpace(list.size());
        super.set(arg0, arr);
	}

}
