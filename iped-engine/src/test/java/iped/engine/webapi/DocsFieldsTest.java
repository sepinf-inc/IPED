package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Unit tests for the /docs/{id}/properties field allowlist (design 06b):
 * default-deny policy, empty fields falling back to DEFAULT_FIELDS, token
 * trimming/deduplication and the exact 400 predicate used by the endpoint
 * (requested field not contained in ALLOWED_FIELDS).
 *
 * Why reflection: parseFields, ALLOWED_FIELDS and DEFAULT_FIELDS are private
 * by design (the allowlist is an implementation detail of the resource), and
 * the public entry point selectedProperties cannot be reached in unit scope
 * without a loaded source (Sources is static state populated at server
 * startup; sourceStringToInt is null here, so the endpoint would fail before
 * exercising the allowlist). These tests therefore invoke the very same
 * private members the endpoint uses; the full HTTP behaviour (404 source
 * first, then 400 for forbidden fields) is covered by the HTTP harness.
 */
public class DocsFieldsTest {

    private static Set<?> allowedFields() throws Exception {
        Field f = Docs.class.getDeclaredField("ALLOWED_FIELDS");
        f.setAccessible(true);
        return (Set<?>) f.get(null);
    }

    private static Set<?> defaultFields() throws Exception {
        Field f = Docs.class.getDeclaredField("DEFAULT_FIELDS");
        f.setAccessible(true);
        return (Set<?>) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> parseFields(String fields) throws Exception {
        Method m = Docs.class.getDeclaredMethod("parseFields", String.class);
        m.setAccessible(true);
        return (Set<String>) m.invoke(null, fields);
    }

    @Test
    public void defaultSetIsTheSixIndexConfirmedFields() throws Exception {
        Set<String> expected = new HashSet<String>(Arrays.asList(
                "hash", "category", "type", "ext", "contentType", "path"));
        assertEquals(expected, defaultFields());
        // Initially the allowlist equals the default set (curated growth only).
        assertEquals(defaultFields(), allowedFields());
    }

    @Test
    public void emptyAbsentOrDelimiterOnlyFieldsFallBackToDefaults() throws Exception {
        Set<?> defaults = defaultFields();
        assertSame(defaults, parseFields(null));
        assertSame(defaults, parseFields(""));
        assertSame(defaults, parseFields("   "));
        assertSame(defaults, parseFields(",, ,"));
    }

    @Test
    public void requestedFieldsAreTrimmedDeduplicatedAndKeptInOrder() throws Exception {
        assertEquals(new LinkedHashSet<String>(Arrays.asList("hash", "path")),
                parseFields(" hash , path ,hash, "));
    }

    @Test
    public void everyDefaultFieldPassesTheAllowlist() throws Exception {
        Set<?> allowed = allowedFields();
        for (Object field : defaultFields()) {
            assertTrue("default field must be allowed: " + field, allowed.contains(field));
        }
    }

    @Test
    public void forbiddenFieldTriggersTheExactBadRequestPredicate() throws Exception {
        Set<?> allowed = allowedFields();
        // "content" (stored text) is not part of the allowlist: the endpoint
        // returns 400 for every requested field outside ALLOWED_FIELDS.
        Set<String> requested = parseFields("hash, content");
        assertTrue(requested.contains("hash"));
        assertTrue(requested.contains("content"));
        assertTrue(allowed.contains("hash"));
        assertFalse("content must not be allowed", allowed.contains("content"));
        // Duplicate the endpoint predicate verbatim (Docs.java selectedProperties):
        boolean rejected = false;
        for (String field : requested) {
            if (!allowed.contains(field)) {
                rejected = true;
                break;
            }
        }
        assertTrue(rejected);
    }

    @Test
    public void unknownOrTypoFieldIsAlsoOutsideTheAllowlist() throws Exception {
        Set<?> allowed = allowedFields();
        assertFalse(allowed.contains("PATH"));      // case sensitive on purpose
        assertFalse(allowed.contains("fulltext"));
        assertFalse(allowed.contains("bookmark"));
    }
}
