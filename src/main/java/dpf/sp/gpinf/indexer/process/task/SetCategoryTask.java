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
package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.Worker;

/*
 * Classe que carrega o mapeamento de mimeTypes->Categoria da aplicação.
 * Além disso utiliza regras javascript de definição de categorias baseadas nas propriedades dos itens.
 * Também é responsável por definir a categoria do item.
 */
public class SetCategoryTask extends AbstractTask{

	public SetCategoryTask(Worker worker) {
		super(worker);
	}

	private static HashMap<String, String> mimetypeToCategoryMap = new HashMap<String, String>();
	private static ArrayList<String[]> mimetypeToCategoryList = new ArrayList<String[]>();
	private static TreeSet<String> categories = new TreeSet<String>(Collator.getInstance());
	private static MediaTypeRegistry registry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();
	
	public static String FOLDER_CATEGORY = "Pastas";

	public static void load(File file) throws FileNotFoundException, IOException {

		categories.add(FOLDER_CATEGORY);
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "windows-1252"));

		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("#"))
				continue;
			String[] keyValuePair = line.split("=");
			if (keyValuePair.length == 2) {
				String category = keyValuePair[0].trim();
				categories.add(category);
				String mimeTypes = keyValuePair[1].trim();
				for (String mimeType : mimeTypes.split(";")) {
					mimeType = mimeType.trim();
					//if (!mimeType.endsWith("*"))
						mimetypeToCategoryMap.put(mimeType, category);
					/*else {
						mimeType = mimeType.substring(0, mimeType.length() - 1);
						String[] pair = { mimeType, category };
						mimetypeToCategoryList.add(pair);
					}*/
				}
			}
		}

		reader.close();

	}
	
	private static String get(MediaType type){
		
		String category;
		do{
			category = mimetypeToCategoryMap.get(type.toString());
			if(category == null)
				category = mimetypeToCategoryMap.get(type.getType());
			if(category != null)
				return category;
				
			type = registry.getSupertype(type);
			
		}while(type != null);
		
		return "";
	}

	private static String get(String mimeType) {

		String category = mimetypeToCategoryMap.get(mimeType);
		if (category != null)
			return category;

		for (String[] pair : mimetypeToCategoryList)
			if (mimeType.startsWith(pair[0]))
				return pair[1];
		

		return "";
	}

	public static TreeSet<String> getCategories() {
		return categories;
	}

	public static void loadScript(File file) throws FileNotFoundException, ScriptException, UnsupportedEncodingException, NoSuchMethodException {

		InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "windows-1252");

		ScriptEngineManager manager = new ScriptEngineManager();
		engine = manager.getEngineByName("javascript");
		engine.eval(reader);
		inv = (Invocable) engine;

		int size = categories.size();
		inv.invokeFunction("addNewCategories", categories);
		if (categories.size() > size)
			refineCategories = true;

	}

	static ScriptEngine engine;
	static Invocable inv;
	static boolean refineCategories = false;

	public void process(EvidenceFile e) throws Exception{
		
		if (e.getCategorySet().size() != 0)
			return;
		
		if (worker.containsReport && !ExportFileTask.hasCategoryToExtract()){
			//Categoria para itens exportados p/ report do FTK sem categoria, ex: anexos
			e.addCategory(Configuration.defaultCategory);
			return;
		}

		String category = get(e.getMediaType());
		e.addCategory(category);

		if (refineCategories)
			inv.invokeFunction("addCategory", e);
	}

	@Override
	public void init() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}

}
