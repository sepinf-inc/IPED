/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.util.ExtraProperties;


/**
 * Parent class for all Package based Test cases
 */
public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext trackingContext;
   protected ParseContext recursingContext;
   protected ParseContext mboxContext;
   
   protected Parser autoDetectParser;
   protected EmbeddedTrackingParser tracker;
   protected EmbeddedMboxParser mboxtracker;

   protected void setUp() throws Exception {
      super.setUp();
      
      mboxtracker = new EmbeddedMboxParser();
      mboxContext = new ParseContext();
      mboxContext.set(Parser.class, mboxtracker);
      
      
      tracker = new EmbeddedTrackingParser();
      trackingContext = new ParseContext();
      trackingContext.set(Parser.class, tracker);
      
      autoDetectParser = new AutoDetectParser();
      recursingContext = new ParseContext();
      recursingContext.set(Parser.class, autoDetectParser);
   }


   @SuppressWarnings("serial")
   protected static class EmbeddedTrackingParser extends AbstractParser {
      protected List<String> filenames = new ArrayList<String>();
      protected List<String> modifieddate = new ArrayList<String>();
      protected List<String> itensmd5 = new ArrayList<String>();
      protected List<String> isfolder = new ArrayList<String>();
      
      public void reset() {
         filenames.clear();
         modifieddate.clear();
         itensmd5.clear();
         isfolder.clear();
      }
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
         return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
         filenames.add(metadata.get(Metadata.RESOURCE_NAME_KEY));
         modifieddate.add(metadata.get(TikaCoreProperties.MODIFIED));
         itensmd5.add(metadata.get(Metadata.CONTENT_MD5));
         if(metadata.get(ExtraProperties.EMBEDDED_FOLDER)!= null)
                 isfolder.add(metadata.get(ExtraProperties.EMBEDDED_FOLDER));
         isfolder.add("false");

      }

   }
   @SuppressWarnings("serial")
   protected static class EmbeddedMboxParser extends AbstractParser {
      protected List<String> messageto = new ArrayList<String>();
      protected List<String> messagefrom = new ArrayList<String>();
      protected List<String> messagesubject = new ArrayList<String>();
      protected List<String> messagebody = new ArrayList<String>();
      protected List<String> messagedate = new ArrayList<String>();
      protected List<String> contenttype = new ArrayList<String>();
      
      public void reset() {
         messageto.clear();
         messagefrom.clear();
         messagesubject.clear();
         messagebody.clear();
         messagedate.clear();
         contenttype.clear();
      }
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
         return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
         messageto.add(metadata.get(Metadata.MESSAGE_TO));
         messagefrom.add(metadata.get(Metadata.MESSAGE_FROM));
         messagesubject.add(metadata.get(ExtraProperties.MESSAGE_SUBJECT));
         messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
         messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
         contenttype.add(metadata.get(HttpHeaders.CONTENT_TYPE));

      }

   }
}
