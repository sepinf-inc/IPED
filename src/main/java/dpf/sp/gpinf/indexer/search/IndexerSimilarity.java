/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.search;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.search.similarities.DefaultSimilarity;

public class IndexerSimilarity extends DefaultSimilarity {

	/*
	 * @Override public float coord(int arg0, int arg1) { // TODO Auto-generated
	 * method stub return 1.0f; }
	 */

	/*
	 * @Override public float idf(long arg0, long arg1) { // TODO Auto-generated
	 * method stub return 1.0f; }
	 */

	@Override
	public float lengthNorm(FieldInvertState state) {
		return state.getBoost();
	}

	@Override
	public float queryNorm(float arg0) {
		return 1.0f;
	}

	/*
	 * @Override public float scorePayload(int arg0, int arg1, int arg2,
	 * BytesRef arg3) { // TODO Auto-generated method stub return 1.0f; }
	 */

	/*
	 * @Override public float sloppyFreq(int arg0) { // TODO Auto-generated
	 * method stub return 1.0f; }
	 */

	/*
	 * @Override public float tf(float freq) { // TODO Auto-generated method
	 * stub return freq*freq; }
	 */

}
