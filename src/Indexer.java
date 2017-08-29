import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Scanner;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.AttributeFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Indexer {
	StandardAnalyzer analyzer;
	Directory index;
	Path indexDirectoryPath = Paths.get("C:/Users/Ryan/workspace/121 Project/index");
	IndexWriterConfig config;
	IndexWriter writer;
	int count = 0;
	
	public static void main(String[] args) {
		Indexer indexer;
		try {
			indexer = new Indexer();
			System.out.print("Create index? (y/n) ");
			Scanner s = new Scanner(System.in);
			String str = s.nextLine();
			if (str.trim().toLowerCase().equals("y")) {
				File file = new File("C:/Users/Ryan/workspace/121 Project/WEBPAGES_RAW/");
				indexer.createIndex(file);
				indexer.printIndexStats();
			}
			indexer.searchIndex();
			s.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public Indexer() throws IOException {
		analyzer = new StandardAnalyzer();
		index = FSDirectory.open(indexDirectoryPath);
		config = new IndexWriterConfig(analyzer);
		config.setOpenMode(OpenMode.CREATE_OR_APPEND);
		writer = new IndexWriter(index, config);
		
	}
	
	public void searchIndex() {
		
		System.out.print("Enter your query: ");
		Scanner s = new Scanner(System.in);
		String querystr = s.nextLine();
		try {
			//Create Query
			Query q = new MultiFieldQueryParser(new String[] {"title", "body", "h1", "h2", "h3", "b"}, analyzer).parse(querystr);
//			System.out.println(q.toString());
			//Create Lucene searcher
			IndexReader reader = DirectoryReader.open(index);
			IndexSearcher searcher = new IndexSearcher(reader);
			
			//Get top 5 docs from search results
			TopDocs retrievedDocs = searcher.search(q, 5);
			ScoreDoc[] results = retrievedDocs.scoreDocs;
			System.out.println();
			System.out.println("=================================================");
			System.out.println("Search Results For " + querystr);
			System.out.println("=================================================");
			//Get top 5 docs from results
			for(int i=0; i<results.length; ++i) {
			    int docID = results[i].doc;
			    Document d = searcher.doc(docID);
			    System.out.println((i + 1) + ") " + d.get("title"));
			    System.out.println("   " + d.get("url"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Check if user wants to search again
		System.out.print("Search again? (y/n) ");
		querystr = s.nextLine();
		if (querystr.trim().toLowerCase().equals("y")) {
			searchIndex();
		}
		s.close();
	}
	
	public void createIndex(File file) {
		indexFile(file);
		System.out.println("RAM kilobytes used: " + writer.ramBytesUsed()/1000);
	}
	
	private void indexFile(File file) {
		//DFS into files
		if (file.isDirectory()) {
			System.out.println(file.getAbsolutePath());
			File[] fileList = file.listFiles();
			for (File f : fileList) {
				indexFile(f);
			}
		}
		else if (!file.isHidden() && file.exists() && file.canRead()) {
			//make doc
			Document doc = getDocument(file);
			try {
				//add doc to writer
				writer.addDocument(doc);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private Document getDocument(File file) {
		Document doc = new Document();
		try {
			//Parse file and add body and title to doc
			org.jsoup.nodes.Document soupDoc = Jsoup.parse(file, "UTF-8");
			String body = soupDoc.getElementsByTag("body").text();
			String title = soupDoc.getElementsByTag("title").text();
			String h1 = soupDoc.getElementsByTag("h1").text();
			String h2 = soupDoc.getElementsByTag("h2").text();
			String h3 = soupDoc.getElementsByTag("h3").text();
			String b = soupDoc.getElementsByTag("b").text();
			addDocument(doc, "body", body, 1);
			addDocument(doc, "title", title, 2);
			addDocument(doc, "url", file.getAbsolutePath(), 1);
			addDocument(doc, "h1", h1, 1.5f);
			addDocument(doc, "h2", h2, 1.25f);
			addDocument(doc, "h3", h3, 1.15f);
			addDocument(doc, "b", b, 1.1f);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			return doc;
		}
		
		return doc;
	}
	
	private void addDocument(Document doc, String fieldTitle, String field, float boost) {
		TextField temp = new TextField(fieldTitle, field, Field.Store.YES);
		temp.setBoost(boost);
		doc.add(temp);
	}
	
	
	public void printIndexStats() {
		try {
			writer.close();
			IndexReader reader = DirectoryReader.open(index);
			System.out.println("Number of Docs: " + reader.numDocs());
			HashSet<String> uniqueWords = new HashSet<String>();
			//for every doc in indexer, create tokenizer
			for (int i=0; i<reader.numDocs(); i++) {
			    Document doc = reader.document(i);
			    AttributeFactory factory = AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY;
			    StandardTokenizer tokenizer = new StandardTokenizer(factory);
			    if (doc.get("body") == null) {
			    	continue;
			    }
			    tokenizer.setReader(new StringReader(doc.get("body")));
			    tokenizer.reset();
			    CharTermAttribute attr = tokenizer.addAttribute(CharTermAttribute.class);
			    while(tokenizer.incrementToken()) {
			        // Grab term and add to unique words
			        String term = attr.toString();
			        uniqueWords.add(term);
			    }
			    tokenizer.close();
			}
			System.out.println("Unique Words: " + uniqueWords.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}