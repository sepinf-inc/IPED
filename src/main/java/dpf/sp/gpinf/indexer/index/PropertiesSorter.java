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
package dpf.sp.gpinf.indexer.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.Collator;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.IndexFiles;

/*
 * Realiza a pré-ordenação das propriedades, agilizando as ordenações no aplicativo de análise,
 * muito lentas caso as strings sejam ordenadas considerando o idioma local.
 */
public class PropertiesSorter {

	String[] fields = { "nome", "tipo", "tamanho", "categoria", "criacao", "modificacao", "acesso", "hash", "deletado", "caminho" };

	private File output;
	private int numThreads;

	public PropertiesSorter(File output, int numThreads) {
		this.output = output;
		this.numThreads = numThreads;
	}

	class Field {
		int id;
		byte[] value;
	}

	class FieldSorter extends Thread {

		private String field;
		private IndexReader reader;
		private Exception exception;

		public FieldSorter(String field, IndexReader reader) {
			this.field = field;
			this.reader = reader;
		}

		@Override
		public void run() {
			try {
				IndexFiles.getInstance().firePropertyChange("mensagem", "", "Pré-ordenando '" + field + "'");
				System.out.println(new Date() + "\t[INFO]\t" + "Pré-ordenando '" + field + "'");

				Comparator<Field> comparator = getComparator(field);

				Field[] array = new Field[reader.maxDoc()];
				for (int i = 0; i < reader.maxDoc(); i++) {
					String value = reader.document(i).get(field);
					if (value == null)
						value = "";
					Field field = new Field();
					field.id = i;
					field.value = value.getBytes("UTF-8");
					array[i] = field;
				}

				Arrays.sort(array, comparator);

				int[] sortedArray = new int[reader.maxDoc()];
				int order = 0;
				Field previous = null;
				for (Field field : array) {
					if (previous != null && comparator.compare(previous, field) != 0)
						order++;
					sortedArray[field.id] = order;
					previous = field;
					if (Thread.interrupted())
						return;
				}

				FileOutputStream fileOut = new FileOutputStream(new File(output, "data/" + field + ".sort"));
				ObjectOutputStream out = new ObjectOutputStream(fileOut);
				out.writeObject(sortedArray);
				out.close();
				fileOut.close();

			} catch (IOException e) {
				exception = e;
			}

		}

		private Comparator<Field> getComparator(final String field) {

			Comparator<Field> comparator = null;
			if (field.equalsIgnoreCase("tamanho")) {
				comparator = new Comparator<Field>() {
					String s1 = "", s2 = "";

					@Override
					public int compare(Field f1, Field f2) {
						try {
							// s1 = f1.value;
							// s2 = f2.value;
							s1 = new String(f1.value, "UTF-8");
							s2 = new String(f2.value, "UTF-8");
							// s1 = reader.document(f1.id).get(field);
							// s2 = reader.document(f2.id).get(field);
						} catch (Exception e1) {
							e1.printStackTrace();
							return 0;
						}
						long i1, i2;
						try {
							i1 = Long.parseLong(s1);
							i2 = Long.parseLong(s2);
							return i1 < i2 ? -1 : (i1 > i2 ? 1 : 0);

						} catch (NumberFormatException e) {
							if (s1.equalsIgnoreCase("")) {
								if (s2.equalsIgnoreCase(""))
									return 0;
								else
									return -1;
							} else
								return 1;
						}

					}
				};
			} else if (field.equalsIgnoreCase("criacao") || field.equalsIgnoreCase("acesso") || field.equalsIgnoreCase("modificacao")) {
				comparator = new Comparator<Field>() {
					DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
					String s1 = "", s2 = "";

					@Override
					public int compare(Field f1, Field f2) {
						try {
							// s1 = f1.value;
							// s2 = f2.value;
							s1 = new String(f1.value, "UTF-8");
							s2 = new String(f2.value, "UTF-8");
							// s1 = reader.document(f1.id).get(field);
							// s2 = reader.document(f2.id).get(field);
						} catch (Exception e1) {
							e1.printStackTrace();
							return 0;
						}
						Date d1, d2;
						try {
							d1 = df.parse(s1);
							d2 = df.parse(s2);
							return d1.compareTo(d2);

						} catch (ParseException e) {
							if (s1.equalsIgnoreCase("")) {
								if (s2.equalsIgnoreCase(""))
									return 0;
								else
									return -1;
							} else
								return 1;
						}
					}
				};
			} else
				comparator = new Comparator<Field>() {
					String s1 = "", s2 = "";
					Collator collator = Collator.getInstance();

					@Override
					public int compare(Field f1, Field f2) {
						try {
							// s1 = f1.value;
							// s2 = f2.value;
							s1 = new String(f1.value, "UTF-8");
							s2 = new String(f2.value, "UTF-8");
							// s1 = reader.document(f1.id).get(field);
							// s2 = reader.document(f2.id).get(field);
							return collator.compare(s1.toLowerCase(), s2.toLowerCase());
						} catch (Exception e) {
							e.printStackTrace();
							return 0;
						}
					}
				};

			return comparator;
		}

	}

	public void sort() throws Exception {

		File indexDir = new File(output, "index");
		Directory directory = FSDirectory.open(indexDir);
		IndexReader reader = IndexReader.open(directory);

		IndexFiles.getInstance().firePropertyChange("mensagem", "", "Pré-ordenando propriedades...");
		System.out.println(new Date() + "\t[INFO]\t" + "Pré-ordenando propriedades...");

		// FieldSorter[] sorter = new FieldSorter[numThreads];
		// consome menos mem.
		FieldSorter[] sorter = new FieldSorter[1];
		int fieldIndex = 0;

		boolean someWorkerAlive = true;
		Exception exception = null;
		while (someWorkerAlive && exception == null) {
			someWorkerAlive = false;
			for (int k = 0; k < sorter.length; k++) {
				if ((sorter[k] == null || !sorter[k].isAlive()) && fieldIndex < fields.length) {
					sorter[k] = new FieldSorter(fields[fieldIndex++], reader);
					sorter[k].start();
					someWorkerAlive = true;
				}
				if (sorter[k] != null && sorter[k].exception != null && exception == null)
					exception = sorter[k].exception;

				if (sorter[k] != null && sorter[k].isAlive())
					someWorkerAlive = true;
			}

			if (IndexFiles.getInstance().isCancelled())
				exception = new InterruptedException("Indexação cancelada!");

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				exception = new InterruptedException("Indexação cancelada!");
			}

		}
		reader.close();

		if (exception != null) {
			for (int k = 0; k < sorter.length; k++)
				if (sorter[k] != null && sorter[k].isAlive()) {
					sorter[k].interrupt();
					sorter[k].join();
				}
			throw exception;
		}

	}

	public void printMem() {
		System.gc();

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		long freemem = Runtime.getRuntime().freeMemory();
		long totalmem = Runtime.getRuntime().totalMemory();
		long usedmem = (totalmem - freemem) / (1024 * 1024);
		System.out.println(usedmem);
	}

}
