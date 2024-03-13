package iped.app.ui.filters;

import java.util.function.Predicate;

/*
 * A PreQueryValueFilter that has a predicate to check if the field value starts with the 
 * defined value.
 * 
 * @author Patrick Dalla Bernardina
 */
public class StartsWithFilter extends ValueFilter {
	String value;
	
    private static class StartsPredicate implements Predicate<String> {
        String value;

        private StartsPredicate(String value) {
            this.value = value.toLowerCase();
        }

        @Override
        public boolean test(String t) {
            return t.startsWith(value);
        }
    }

    public StartsWithFilter(String field, String value) {
        super(field, new StartsPredicate(value));
        this.value = value;
    }

    public String toString() {
        return field + "^=\"" + value + "\"";
    }
}