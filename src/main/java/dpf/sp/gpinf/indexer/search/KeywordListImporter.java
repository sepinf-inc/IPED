package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.CancelableWorker;
import dpf.sp.gpinf.indexer.util.IOUtil;

public class KeywordListImporter extends CancelableWorker{
	
	ProgressDialog progress;
	ArrayList<String> keywords, result = new ArrayList<String>();

	public KeywordListImporter(File file){
		
		
		try {
			keywords = IOUtil.loadKeywords(file.getAbsolutePath(), Charset.defaultCharset().displayName());
			
			progress = new ProgressDialog(App.get(), this, false);
			progress.setMaximum(keywords.size());
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected Object doInBackground() throws Exception {
		
		int i = 0;
		for(String keyword : keywords){
			if(this.isCancelled())
				break;
			PesquisarIndice task = new PesquisarIndice(PesquisarIndice.getQuery(keyword));
			if(task.pesquisarTodos().length > 0)
				result.add(keyword);
			
			final int j = ++i;
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					progress.setProgress(j);
				}
			});
		}
		
		return null;
	}
	
	@Override
	public void done(){
		progress.close();
		
		if (App.get().marcadores.typedWords.size() == 0)
			App.get().termo.addItem(Marcadores.HISTORY_DIV);
		
		for (String word : result){
			App.get().termo.addItem(word);
			App.get().marcadores.typedWords.add(word);
		}
	
		App.get().marcadores.saveState();
	}
	
}
