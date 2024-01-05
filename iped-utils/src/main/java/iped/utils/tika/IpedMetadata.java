package iped.utils.tika;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.apache.tika.metadata.writefilter.MetadataWriteFilter;

public class IpedMetadata extends SyncMetadata {
    class IpedMetadataFilter implements MetadataWriteFilter {
        String arr[];
        int pos = 0;

        @Override
        public void add(String field, String value, Map<String, String[]> data) {
            if (arr == null) {
                reset();
            }
            arr[pos] = value;
            pos++;
            if (pos == arr.length) {
                String[] values = data.get(field);
                if (values == null) {
                    data.put(field, arr);
                } else {
                    String[] merge = new String[values.length + arr.length];
                    System.arraycopy(values, 0, merge, 0, values.length);
                    System.arraycopy(arr, 0, merge, values.length, arr.length);
                    data.put(field, merge);
                }
                reset();
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

        public void reset() {
            arr = new String[1];
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
