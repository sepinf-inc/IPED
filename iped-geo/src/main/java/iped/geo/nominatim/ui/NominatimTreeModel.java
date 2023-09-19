package iped.geo.nominatim.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import iped.geo.nominatim.NominatimTask;
import iped.viewers.api.IMultiSearchResultProvider;

public class NominatimTreeModel implements TreeModel {
    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    IMultiSearchResultProvider app;
    static String ROOT = "Nominatim Entities";

    private static boolean modelLoaded = false;

    public NominatimTreeModel(IMultiSearchResultProvider app) {
        this.app = app;
    }

    @Override
    public Object getRoot() {
        return ROOT;
    }

    class Region implements Comparable<Region> {
        String name;
        String titleName;

        public Region(String name) {
            this.name = name;

            // upercase all first letters
            Pattern TITLE_CASE_PATTERN = Pattern.compile("(\\s)([a-z])");
            String lastName = name.substring(name.lastIndexOf(":") + 1);
            Matcher matcher = TITLE_CASE_PATTERN.matcher(lastName);
            StringBuilder output = new StringBuilder();
            output.append(lastName.substring(0, 1).toUpperCase());
            int lastEnd = 1;
            while (matcher.find()) {
                output.append(lastName.substring(lastEnd, matcher.start()));
                output.append(matcher.group(1));
                output.append(matcher.group(2).toUpperCase());
                lastEnd = matcher.end();
            }
            output.append(lastName.substring(lastEnd));
            titleName = output.toString();
        }

        public String toString() {
            return titleName;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Region) && (((Region) obj).name.equals(name));
        }

        @Override
        public int compareTo(Region o) {
            // TODO Auto-generated method stub
            return this.name.compareTo(o.name);
        }

    }

    class Country extends Region {
        public Country(String name) {
            super(name);
        }
    }

    class State extends Region {
        Country country;

        public State(Country country, String name) {
            super(name);
            this.country = country;
        }
    }

    class City extends Region {
        Country country;
        State state;

        public City(Country country, State state, String name) {
            super(name);
            this.country = country;
            this.state = state;
        }
    }

    class Suburb extends Region {
        Country country;
        State state;
        City city;

        public Suburb(Country country, State state, City city, String name) {
            super(name);
            this.country = country;
            this.state = state;
            this.city = city;
        }
    }

    Country lastCountry;
    State[] lastStatesArray;
    State lastState;
    City[] lastCitiesArray;
    City lastCity;
    Suburb[] lastSuburbsArray;
    Suburb lastSuburb;

    ArrayList<Country> countries;
    TreeMap<Country, State[]> statesMap = new TreeMap<>();
    TreeMap<State, City[]> citiesMap = new TreeMap<>();
    TreeMap<City, Suburb[]> suburbsMap = new TreeMap<>();

    public ArrayList<Country> getCountries() {
        if (countries == null) {
            countries = new ArrayList<>();
            LeafReader reader = app.getIPEDSource().getLeafReader();

            try {
                SortedSetDocValues ssdv = reader.getSortedSetDocValues(NominatimTask.NOMINATIM_COUNTRY_METADATA);
                TermsEnum te = ssdv.termsEnum();
                BytesRef br = te.next();
                while (br != null) {
                    countries.add(new Country(br.utf8ToString()));
                    br = te.next();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return countries;
    }

    public State[] getStates(Country country) {
        if (country == lastCountry && lastStatesArray != null) {
            return lastStatesArray;
        }
        State[] statesArray = statesMap.get(country);
        if (statesArray == null) {
            HashSet<State> states = new HashSet<State>();
            LeafReader reader = app.getIPEDSource().getLeafReader();
            boolean found = false;

            try {
                SortedSetDocValues ssdv = reader.getSortedSetDocValues(NominatimTask.NOMINATIM_STATE_METADATA);
                TermsEnum te = ssdv.termsEnum();
                BytesRef br = te.next();
                while (br != null) {
                    String stateCompleteName = br.utf8ToString();
                    if (stateCompleteName.startsWith(country.name)) {
                        found = true;
                        states.add(new State(country, stateCompleteName));
                    } else {
                        if (found) {
                            // it is not necessary to loop till the end as the data is sorted
                            break;
                        }
                    }
                    br = te.next();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            statesArray = states.toArray(new State[0]);
            statesMap.put(country, statesArray);
        }

        lastCountry = country;
        lastStatesArray = statesArray;

        return statesArray;
    }

    public City[] getCities(State state) {
        if (state == lastState && lastCitiesArray != null) {
            return lastCitiesArray;
        }
        City[] citiesArray = citiesMap.get(state);
        if (citiesArray == null) {
            HashSet<City> cities = new HashSet<City>();

            LeafReader reader = app.getIPEDSource().getLeafReader();
            boolean found = false;

            try {
                SortedSetDocValues ssdv = reader.getSortedSetDocValues(NominatimTask.NOMINATIM_CITY_METADATA);
                TermsEnum te = ssdv.termsEnum();
                BytesRef br = te.next();
                while (br != null) {
                    String cityCompleteName = br.utf8ToString();
                    if (cityCompleteName.startsWith(state.name)) {
                        found = true;
                        cities.add(new City(state.country, state, cityCompleteName));
                    } else {
                        if (found) {
                            // it is not necessary to loop till the end as the data is sorted
                            break;
                        }
                    }
                    br = te.next();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            citiesArray = cities.toArray(new City[0]);
            citiesMap.put(state, citiesArray);
        }

        lastState = state;
        lastCitiesArray = citiesArray;

        return citiesArray;
    }

    public Suburb[] getSuburbs(City city) {
        if (city == lastCity && lastSuburbsArray != null) {
            return lastSuburbsArray;
        }
        Suburb[] suburbsArray = suburbsMap.get(city);
        if (suburbsArray == null) {
            HashSet<Suburb> suburbs = new HashSet<Suburb>();

            LeafReader reader = app.getIPEDSource().getLeafReader();
            boolean found = false;

            try {
                SortedSetDocValues ssdv = reader.getSortedSetDocValues(NominatimTask.NOMINATIM_SUBURB_METADATA);
                TermsEnum te = ssdv.termsEnum();
                BytesRef br = te.next();
                while (br != null) {
                    String suburbCompleteName = br.utf8ToString();
                    if (suburbCompleteName.startsWith(city.name)) {
                        found = true;
                        suburbs.add(new Suburb(city.country, city.state, city, suburbCompleteName));
                    } else {
                        if (found) {
                            // it is not necessary to loop till the end as the data is sorted
                            break;
                        }
                    }
                    br = te.next();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            suburbsArray = suburbs.toArray(new Suburb[0]);
            suburbsMap.put(city, suburbsArray);
        }

        lastCity = city;
        lastSuburbsArray = suburbsArray;

        return suburbsArray;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (!modelLoaded) {
            return null;
        }
        if (parent == ROOT) {
            return getCountries().get(index);
        }
        if (parent instanceof Country) {
            return getStates((Country) parent)[index];
        }
        if (parent instanceof State) {
            return getCities((State) parent)[index];
        }
        if (parent instanceof City) {
            return getSuburbs((City) parent)[index];
        }
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (!modelLoaded) {
            return 0;
        }
        if (parent == ROOT) {
            return getCountries().size();
        }
        if (parent instanceof Country) {
            return getStates((Country) parent).length;
        }
        if (parent instanceof State) {
            return getCities((State) parent).length;
        }
        if (parent instanceof City) {
            return getSuburbs((City) parent).length;
        }
        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        if (!modelLoaded) {
            return true;
        }
        if (node == ROOT) {
            return getCountries().size() == 0;
        }
        if (node instanceof Country) {
            return getStates((Country) node).length == 0;
        }
        if (node instanceof State) {
            return getCities((State) node).length == 0;
        }
        if (node instanceof City) {
            return getSuburbs((City) node).length == 0;
        }
        return false;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        // TODO Auto-generated method stub

    }

    public static void install() {
        modelLoaded = true;
    }

}
