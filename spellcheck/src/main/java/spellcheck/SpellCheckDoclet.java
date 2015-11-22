/*
 * Copyright (C) 2004-2006 Soft Frame Works.  All rights reserved.
 * Use is subject to license terms.
 * ----------------------------------------------------------------------------
 *  The Spell Check Declet is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License as published
 *  by the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  The Spell Check Declet is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */
 
package spellcheck;
 
import com.swabunga.spell.engine.Configuration;
import com.swabunga.spell.engine.SpellDictionary;
import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.event.SpellCheckEvent;
import com.swabunga.spell.event.SpellCheckListener;
import com.swabunga.spell.event.SpellChecker;
import com.swabunga.spell.event.StringWordTokenizer;

import com.sun.javadoc.*;
import com.sun.tools.doclets.Taglet;
import com.sun.tools.javadoc.*;

import java.io.*;
import java.lang.Exception;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Class SpellCheckDoclet implements a Doclet which allows you to spell-check your Javadoc
 * documentation.  This Doclet checks the spelling of the JavaDoc comments in your Java 
 * source-code files, as well as checking the spelling of the text in the associated HTML 
 * files (e.g. "package.html").  This Doclet uses the open-source Jazzy Spell Checker for
 * its spell-checking capabilities.  Thus, you must download and install the Jazzy Spell 
 * Checker in order to use the Doclet.  The Jazzy Spell Checker is available for download
 * at <A href="http://sourceforge.net/projects/jazzy">sourceforge.net</A>.
 *
 * <P>This Doclet spell-checks the Javadoc comments for each Java Source Code file in your 
 * application.  For each such Java file, the class Javadoc comment is checked, and the 
 * Javadoc comment for each class member (field, constructor, and method) is also checked.  
 * The HTML files used for Javadoc documentation can also optionally be checked.  If you
 * choose to do this, then every HTML file found in the directories that contain your Java
 * files is spell-checked.  The results of the spell-check are written to a text file 
 * ("CheckDoc.txt").  
 *
 * <P>This Doclet is made available under an open-source license (the GNU General Public 
 * License), and is available for free download at 
 * <A href="http://www.softframeworks.com/download/download.php">www.softframeworks.com</A>.
 *
 * <H3>History</H3>
 *
 * <B>Version 1.0.1</B>
 * <P>Fixed bug in method <CODE>spellCheckHtmlFile(File theFile)</CODE>.  This bug caused
 * a Null Pointer Exception to be thrown if the BODY element in the HTML file is in 
 * lower-case.
 *
 * <B>Version 1.0.2</B>
 * <P>Fixed bug in method <CODE>spellCheckHtmlFiles(PackageDoc thePackageDoc)</CODE>.  This 
 * bug caused a NPE to be thrown if the package.html file is not present.  Also added 
 * a println to display the package name to the console.
 *
 * @version 1.0.2
 * @author  hlander
 */
public class SpellCheckDoclet 
{   
  //-----------------------------------------------------------------------
  // Inner Classes.
  //-----------------------------------------------------------------------    
  
  /**
   * SpellErrorHandler.
   */  
  private static class SpellErrorHandler
    implements SpellCheckListener   
  {
    /**
     * Handles spelling error events.
     * SpellCheckListener interface implementation.
     */
    public void spellingError(SpellCheckEvent theEvent) 
    {     
      String theInvalidWord = theEvent.getInvalidWord();
      
      // Certain spelling errors are ignored.
      // NOTE: This does not pertain to Ignore Words, since Ignore Words do not raise a
      //       Spell Check Evant.  Rather it pertains to Ignor Containing strings.
      if (isToIgnore(theInvalidWord)) return;
 
      
      // Write the Package Header (if not already written).
      if (currentPackage != lastPackageHeaderDoc)
      {
        writePackageHeader(currentPackage);
      }
      
      // If a file is being processed...
      if (currentFile != null)
      {
        // Write the File Header (if not already written).
        if (currentFile != lastHeaderFile)
        {
          writeFileHeader(currentFile);
          OUT_STREAM.print("\t");
        }          
      }
      
      // Else, if a class or class member is being processed...
      else
      {
        // Write the Class Header (if not already written).
        if (currentClass != lastClassHeaderDoc)
        {
          writeClassHeader(currentClass);
          OUT_STREAM.print("\t");
        }  
        
        // Write the Member Header (if not already written).      
        if ((currentMember != null) && (currentMember != lastMemberHeaderDoc))
        {
          writeMemberHeader(currentMember);
          OUT_STREAM.print("\t");
        }  
      }
      /**
      // rls added
      OUT_STREAM.println("#" + currentClass.position().toString());
      OUT_STREAM.println("\t#line:" + currentClass.position().line() 
              + ";position:" + (currentClass.position().column() - theInvalidWord.length())
              + ";word:" + theInvalidWord);
      OUT_STREAM.print("\t");
      // end rls added
      **/
      unknownWords.add(theInvalidWord); //rls
      // Write the invalid word.
      OUT_STREAM.print("  " + theInvalidWord);
      
      // Write the suggests.
      if (withSuggestions)
      {
        OUT_STREAM.print(": ");
        
        List theSuggestions = theEvent.getSuggestions();
        if (theSuggestions.size() > 0) 
        {
          for (Iterator theSuggestedWord = theSuggestions.iterator(); theSuggestedWord.hasNext();) 
          {
            OUT_STREAM.print(theSuggestedWord.next() + " ");
          }
        } 
        else 
        {
          OUT_STREAM.print("<No suggestions>");
        }    
        OUT_STREAM.print("\n\t");
      }  
      
      // Increment the error count.
      errorCount++;
    }

    /**
     * Determines if the given Invalid Word is to be ignored.  If it contains one of the
     * Specified Ignore Substrings, then TRUE is returned, else FALSE is returned.
     */
    private boolean isToIgnore(String theInvalidWord)    
    {
      String theIgnoreSubstring = null;
      int    theCount = IGNORE_CONTAINING.size();
      
      for (int ii=0; ii<theCount; ii++)
      {
        theIgnoreSubstring = (String)IGNORE_CONTAINING.elementAt(ii);
        if (theInvalidWord.indexOf(theIgnoreSubstring) >= 0) return true;
      }
      
      return false;
    }
  }  
    
  /**
   * HtmlFileFilter.
   */  
  private static class HtmlFileFilter
    implements FileFilter, Serializable
  {
    public boolean accept(File theFile)
    {
      if (theFile.isDirectory()) return false;
      
      return theFile.getName().toLowerCase().endsWith(".html");
    }
  }

    
  //-----------------------------------------------------------------------
  // Class variables/constants.
  //----------------------------------------------------------------------- 

  /**
   * The Spell Check Doclet title.
   */  
  public  static final String         TITLE = "Spell Check Doclet";

  /**
   * The Spell Check Doclet version.
   */  
  public  static final String         VERSION = "1.0.2";
  
  
  private static final HtmlFileFilter HTML_FILE_FILTER = new HtmlFileFilter();
  private static final SpellChecker   SPELL_CHECKER = buildSpellChecker();
  private static final PrintStream    OUT_STREAM;
  private static final Vector         IGNORE_CONTAINING = new Vector();
  // rls added
  private static final ConcurrentSkipListSet<String> unknownWords = new ConcurrentSkipListSet<String>();
  
  /**
   * The package currently being spell-checked.
   */  
  private static PackageDoc currentPackage = null;
  
  /**
   * The package for which the most recently header was written.
   */  
  private static PackageDoc lastPackageHeaderDoc = null;
  
  /**
   * The file currently being spell-checked.
   */  
  private static File currentFile = null;
  
  /**
   * The file for which the most recently header was written.
   */  
  private static File lastHeaderFile = null;
  
  /**
   * The class currently being spell-checked.
   */  
  private static ClassDoc currentClass = null;
  
  /**
   * The class for which the most recently header was written.
   */  
  private static ClassDoc lastClassHeaderDoc = null;
  
  /**
   * The class member (field, constructor, or method) currently being spell-checked.
   */  
  private static MemberDoc currentMember = null;
  
  /**
   * The class member (field, constructor, or method) for which the most recently 
   * header was written.
   */  
  private static MemberDoc lastMemberHeaderDoc = null;

  /**
   * The spelling error count.
   */  
  private static int errorCount = 0;

  /**
   * At least one dictionary must be specified.
   */  
  private static boolean addedDictionary = false;

  /**
   * The inputs specified by the parameters can optionally be echoed to the console.
   */  
  private static boolean echoInputs = true;
  
  /**
   * Flag used to identify the first command line option being validated.
   */  
  private static boolean firstOption = true;

  /**
   * Correct spelling suggestions can optionally be included.
   */  
  private static boolean withSuggestions = false;

  /**
   * Documentation HTML files can optionally be checked.
   */  
  private static boolean checkHtmlFiles = false;

  
  //-----------------------------------------------------------------------
  // Class initialization.
  //----------------------------------------------------------------------- 
  static
  {
    PrintStream theOutStream = null;
    
    try
    {
      theOutStream = new PrintStream("CheckDoc.txt");
    }
    catch (Throwable theEx)
    {
      theOutStream = System.out;
    }
    
    OUT_STREAM = theOutStream;
  }
  

  //-----------------------------------------------------------------------
  // Public methods.
  //-----------------------------------------------------------------------

  /**
   * Returns the "length" of the given command-line option. If an option takes no arguments, 
   * its length is one.   If it takes one argument, it's length is two, and so on. This method 
   * is called by JavaDoc to parse the options it does not recognize.  Zero is returned if 
   * the option is not recognized, and a negative value is returned if an error occurs.
   *
   * @NOTE This method is invoked by JavaDoc. 
   */
  public static int optionLength(String theOption) 
  {        
    theOption = theOption.toLowerCase();
    
    // NOTE: The length of the arguments known to JavaDoc does not need to be specified.
    if (theOption.equals("-dictionary") || theOption.equals("-ignore") || 
        theOption.equals("-ignorecontaining") || theOption.equals("-ignorefile") || 
        theOption.equals("-echo"))
    {
      return 2;
    }
    if (theOption.equals("-withsuggestions") || theOption.equals("-checkhtmlfiles"))
    {
      return 1;
    }  
    

    return 0;
  }

  /**
   * Starts the spell-check process.  The given Root Doc provides access to the results 
   * of the JavaDoc analysis of the Java source files.  This Root Doc is used to obtain
   * access to the Javadoc comments whose content is spell-checked.
   *
   * @NOTE This method is invoked by JavaDoc. 
   */  
  public static boolean start(RootDoc theRootDoc) 
  {
    // Make sure that a dictionary was specified.
    if (!addedDictionary)
    {
      System.out.println("\n*** ERROR: At least one dictionary must be specified.");
      return false;
    }
    
    // Print the Herald.
    OUT_STREAM.println("**************************************************************************************");
    OUT_STREAM.println(TITLE + " version " + VERSION);
    OUT_STREAM.println(new Date());
    OUT_STREAM.println("Open Source download: http://www.softframeworks.com/download/download.php");
    OUT_STREAM.println("**************************************************************************************\n");
    
    // Spell check the API.
    spellCheckApi(theRootDoc);  
    
    // Write the error count.
    OUT_STREAM.println("\n\n***********************\nError Count: " + errorCount + "\n***********************");

    writeUnknownWords(); // rls added

    return true;
  }

  private static void writeUnknownWords() {

    if (!unknownWords.isEmpty()) {
      PrintStream outFile = null;
      try {
        outFile = new PrintStream("UnknownWordsDoc.txt");

        for (String s : unknownWords) {
          outFile.println(s);
        }

      } catch(Exception e) {

      } finally {
        if (outFile != null) {
          outFile.close();
        }
      }
    }
    /**
    PrintStream theOutStream = null;

    try
    {
      theOutStream = new PrintStream("CheckDoc.txt");
    }
    catch (Throwable theEx)
    {
      theOutStream = System.out;
    }
**/
  }

 /**
  * Validates the command-line options.  Returns true if they are valid, else false.
  *
  * @NOTE This method is invoked by JavaDoc. 
  */  
  public static boolean validOptions(String theOptionList[][], DocErrorReporter theErrorReporter) 
  {
    String[] theOption = null;

    // Display the Herald to the console.
    if (firstOption)
    {
      System.out.println("\n" + TITLE + " version " + VERSION + 
                         "\n==========================================\n");
      firstOption = false;
    }
      
    // Process the options.
    for (int ii=0; ii<theOptionList.length; ii++) 
    {
      theOption = theOptionList[ii]; 
      theOption[0] = theOption[0].toLowerCase();
      
      // Dictionary.
      if (theOption[0].equals("-dictionary")) 
      {
        if (echoInputs) System.out.println("  ::: Dictionary: " + theOption[1]); 
        addDictionary(theOption[1]);
      } 
      
      // Ignore.
      else if (theOption[0].equals("-ignore")) 
      {
        if (echoInputs) System.out.println("  >>> Ignore: " + theOption[1]); 
        SPELL_CHECKER.ignoreAll(theOption[1]);
      } 
      
      // Ignore File.
      else if (theOption[0].equals("-ignorefile")) 
      {
        processIgnoreFile(theOption[1]);
      }
      
      // Ignore Containing.
      else if (theOption[0].equals("-ignorecontaining")) 
      {
        if (echoInputs) System.out.println("  >>> Ignore Containing: " + theOption[1]); 
        IGNORE_CONTAINING.add(theOption[1]);
      }      
      
      // Echo.
      else if (theOption[0].equals("-echo")) 
      {
        theOption[1] = theOption[1].toLowerCase();
        if (theOption[1].equals("off")) 
        {
          echoInputs = false;
        }
        else if (theOption[1].equals("on")) 
        {
          echoInputs = true;
        }
        else
        {
          System.out.println("\n*** ERROR: Invalid ECHO value: " + theOption[1] +
                             "\n           Must be 'ON' or 'OFF'.");
          return false;          
        }  
      }
      
      // With Suggestions.
      if (theOption[0].equals("-withsuggestions"))
      {
        withSuggestions = true;
      } 
      
      // Check Html Files.
      else if (theOption[0].equals("-checkhtmlfiles"))
      {
        checkHtmlFiles = true;
      }  
    }  

    return true;
  }
  
  
  //-----------------------------------------------------------------------
  // Private methods.
  //-----------------------------------------------------------------------
  
  /**
   * Adds a dictionary to the Spell Checker.
   */
  private static void addDictionary(String theWordListFile) 
  {
    SpellDictionary theDictionary = null;
    
    try 
    {
      // Create the Dictionary and add it to the Spell Checker.
      theDictionary = new SpellDictionaryHashMap(new File(theWordListFile));
      SPELL_CHECKER.addDictionary(theDictionary);
    } 
    catch (Exception theEx) 
    {
      System.out.println("*** ERROR encountered adding dictionary: " + theWordListFile);
      theEx.printStackTrace();
      System.exit(-1);
    }
    
    // Set the flag.
    addedDictionary = true;
  }
  
  /**
   * Builds the Spell Checker.
   */
  private static SpellChecker buildSpellChecker() 
  {
    Configuration theConfiguration = null;
    SpellChecker  theSpellChecker = null;
    
    try 
    {
      // Create the Spell Checker.
      theSpellChecker = new SpellChecker();
      
      // Configure the Spell Checker.
      theConfiguration = theSpellChecker.getConfiguration();
      theConfiguration.setBoolean(Configuration.SPELL_IGNOREMIXEDCASE, true);

      // Specify the words to be ignored.
      // NOTE: Ignore commonly abbreviations: e.g and i.e.
      theSpellChecker.ignoreAll("e.g");
      theSpellChecker.ignoreAll("i.e");
      
      // Add the Spell Check Listener.
      theSpellChecker.addSpellCheckListener(new SpellErrorHandler());
    } 
    catch (Exception theEx) 
    {
      theEx.printStackTrace();
      System.exit(-1);
    }
    
    return theSpellChecker;
  }
  
  /**
   * Gets the content of the given file as a string.  An exception is thrown if the file
   * does not exist or is a directory.
   *
   * @NOTE The length of the file is obtained as a long value and cast to an int value.  This
   *       means that this method cannot be used for files longer than the value that can be 
   *       contained in an int variable.
   */
  private static StringBuffer getFileContentString(File theFile)
    throws Exception
  {
    BufferedInputStream theInputFile = null;
    StringBuffer        theBuffer = null;
    String              theMsg = null;
    int                 theByte = 0;

    // Make sure that the input file exists and is not a directory.
    if (!theFile.exists())
    {
      theMsg = "The specified input file does not exist: " + theFile.toString();
      throw new Exception(theMsg);
    }
    if (theFile.isDirectory())
    {
      theMsg = "The input file cannot be a directory: " + theFile.toString();
      throw new Exception(theMsg);
    }    
    
    // Allocate the String Buffer to hold the contents of the file.
    theBuffer = new StringBuffer((int)theFile.length());
    
    // Open the Input file.
    theInputFile = new BufferedInputStream(new FileInputStream(theFile));
      
    // Read the contents of the Input File into the String Buffer.
    while((theByte = theInputFile.read()) >= 0)
    {
      theBuffer.append((char)theByte);
    }  
      
    // Close the Input file.
    theInputFile.close();
    
    return theBuffer;
  }

  /**
   * Processes the given Ignore File.
   */
  private static void processIgnoreFile(String theFileName) 
  {
    LineNumberReader theLineReader = null;
    String           theIgnoreWord = null;
    
    try
    {
      // Create the file reader.
      theLineReader = new LineNumberReader(new FileReader(theFileName));

      // Read the file one line at a time.
      while ((theIgnoreWord = theLineReader.readLine()) != null)
      {
        // Add the word contained on this line to the ignored words.
        theIgnoreWord = theIgnoreWord.trim();
        if (theIgnoreWord.length() > 0)
        {
          if (echoInputs) System.out.println("  >>> Ignore: " + theIgnoreWord); 
          SPELL_CHECKER.ignoreAll(theIgnoreWord);
        }  
      } 
    }
    catch (Throwable theEx)
    {
      System.out.println("*** ERROR encountered processing Ignore File: " + theFileName);
      theEx.printStackTrace();
      System.exit(-1);
    }  
  }
    
  /**
   * Spell-checks the API identified by the given Root Doc.
   */  
  private static void spellCheckApi(RootDoc theRootDoc) 
  {
    ClassDoc[] theClassList = theRootDoc.classes();

    // rls debug
    for(int j=0; j < theClassList.length; j++) {
      System.out.println("#-# " + theClassList[j]);
    }

    // Spell check the classes.
    if (OUT_STREAM != System.out)
    {
      System.out.println("\nSpell Checking classes...");
      for (int ii=0; ii<theClassList.length; ii++)
      {
        spellCheckClass(theClassList[ii]);
        System.out.print(".");
      }
      System.out.println("\nDone.\n");
    }
    else
    {
      for (int ii=0; ii<theClassList.length; ii++)
      {
        spellCheckClass(theClassList[ii]);
      }
    }  
  }
    
  /**
   * Spell-checks the Javadoc for the given class.
   */
  private static void spellCheckClass(ClassDoc theClassDoc) 
  {
    PackageDoc  theContainingPackage = theClassDoc.containingPackage();
    MemberDoc[] theMemberList = null;
    
    // If a new Package is being started, then spell check the HTML files associated 
    // with this package before the class is checked.
    if (checkHtmlFiles && (theContainingPackage != currentPackage))
    {
      currentPackage = theContainingPackage;
      spellCheckHtmlFiles(theContainingPackage);
    }  

    // Record the current package, class, and member.
    // NOTE: The Current Member must be set to NULL to indicate that the class comment
    //       is being processed.
    currentPackage = theContainingPackage;
    currentClass = theClassDoc;
    currentMember = null; 
    
    // Check the class comment.
    spellCheckString(theClassDoc.commentText());
    
    // Check the fields.
    theMemberList = theClassDoc.fields();
    for (int ii=0; ii<theMemberList.length; ii++)
    {
      spellCheckMember(theMemberList[ii]);
    }
    
    // Check the constructors.
    theMemberList = theClassDoc.constructors();
    for (int ii=0; ii<theMemberList.length; ii++)
    {
      spellCheckMember(theMemberList[ii]);
    }
    
    // Check the methods.
    theMemberList = theClassDoc.methods();
    for (int ii=0; ii<theMemberList.length; ii++)
    {
      spellCheckMember(theMemberList[ii]);
    }
  } 
  
  /**
   * Spell-checks the contents of the given HTML file.
   */
  private static void spellCheckHtmlFile(File theFile) 
  {
    StringBuffer theFileContentBuffer = null;
    String       theFileContent = null;
    int          theBodyStartIndex = 0;
    
    try
    {
      currentFile = theFile;
      
      // Get the content of the file.
      theFileContentBuffer = getFileContentString(theFile);
      
      // Get the portion of the file starting from the <BODY> element.  This is the
      // portion of the file that is spell checked.
      // NOTE: The code assumes that the BODY element appears in either all upper-case
      //       or all lower-case.
      theBodyStartIndex = theFileContentBuffer.indexOf("<BODY");
      if (theBodyStartIndex < 0)
      {
        theBodyStartIndex = theFileContentBuffer.indexOf("<body");
      }
      if (theBodyStartIndex > 0)
      {
        theFileContent = theFileContentBuffer.substring(theBodyStartIndex);
      }
      else
      {
        theFileContent = theFileContentBuffer.toString();
      }  
      
      // Spell check this portion of the file.
      spellCheckString(theFileContent);
      
      currentFile = null;
    }
    catch (Throwable theEx)
    {
      System.out.println("*** ERROR encountered reading file: " + theFile);
      theEx.printStackTrace();
      System.exit(-1);
    } 
  }
  
  /**
   * Spell-checks the HTML files associated with for the given package.
   */
  private static void spellCheckHtmlFiles(PackageDoc thePackageDoc) 
  {
    SourcePosition theFilePosition = thePackageDoc.position();
    File           theDirectory = null;
    String         theSubdirectory = null;
    String         theMsg = null;
    
    // Display the package name.
    System.out.print("\n  >>> " + thePackageDoc);
    
    // Check for NULL File Position.  This indicates that the package.html file 
    // is not present.
    if (theFilePosition == null)
    {
      theMsg = "  ****WARNING: The package.html file is not present for this package.\n" +
               "               The HTML files cannot be spellchecked until this file " +
                              "is added.";
      System.out.println("\n\n" + theMsg);
      return;
    }  
 
    // Get the directory associated with the given Package.
    theDirectory = theFilePosition.file();
    if (!theDirectory.isDirectory())
    {
      theDirectory = theDirectory.getParentFile();
    }  
    
    // Spell-check the HTML files in this directory.
    spellCheckHtmlFiles(theDirectory); 
    
    // Spell-check the HTML files in the doc-files subdirectory (if it exists).
    theSubdirectory = theDirectory.toString() + File.separatorChar + "doc-files";
    theDirectory = new File(theSubdirectory);
    if (theDirectory.exists())
    {
       spellCheckHtmlFiles(theDirectory);
    }  
  }
  
  /**
   * Spell-checks the HTML files in the given directory.
   */
  private static void spellCheckHtmlFiles(File theDirectory) 
  {
    File[] theHtmlFileList = theDirectory.listFiles(HTML_FILE_FILTER);

    // Check the HTML files in the given directory.
    for (int ii=0; ii<theHtmlFileList.length; ii++)  
    {
      spellCheckHtmlFile(theHtmlFileList[ii]);
    }
  }
  
  /**
   * Spell-checks the Javadoc for the the given class member (field, constructor, or method).
   */
  private static void spellCheckMember(MemberDoc theMemberDoc) 
  {
    currentMember = theMemberDoc;
    
    // Check the member comment.
    spellCheckString(theMemberDoc.commentText());
  } 
  
  /**
   * Spell-checks the given string.
   */
  private static void spellCheckString(String theString) 
  {
    try 
    {
      SPELL_CHECKER.checkSpelling(new StringWordTokenizer(theString));
    } 
    catch (Exception theEx) 
    {
      theEx.printStackTrace();
    }
  }  

  /**
   * Writes the Class Header.
   */
  private static void writeClassHeader(ClassDoc theClassDoc) 
  {  
    OUT_STREAM.println("\n\n  Class: " + theClassDoc + 
                       "\n  ================================================");
                       
    lastClassHeaderDoc = theClassDoc;
  }

  /**
   * Writes the File Header.
   */
  private static void writeFileHeader(File theFile) 
  {  
    OUT_STREAM.println("\n\n  File: " + theFile + 
                       "\n  ================================================");
                       
    lastHeaderFile = theFile;
  }

  /**
   * Writes the Member (field, constructor, or method) Header.
   */
  private static void writeMemberHeader(MemberDoc theMemberDoc) 
  {  
    if (theMemberDoc instanceof MethodDoc)
    {  
      OUT_STREAM.println("\n\n    Method: " + theMemberDoc.name() + 
                         "\n    --------------------------------------");
    }
    else if (theMemberDoc instanceof ConstructorDoc)
    {  
      OUT_STREAM.println("\n\n    Constructor: " + theMemberDoc.name() + 
                         "\n    --------------------------------------");
    } 
    else if (theMemberDoc instanceof FieldDoc)
    {  
      OUT_STREAM.println("\n\n    Field: " + theMemberDoc.name() + 
                         "\n    --------------------------------------");
    } 
    
    lastMemberHeaderDoc = theMemberDoc;
  }
  
  /**
   * Writes the Package Header.
   */
  private static void writePackageHeader(PackageDoc thePackageDoc) 
  {  
    OUT_STREAM.println("\n\n*********************************************" +
                       "\nPackage: " + thePackageDoc + 
                       "\n*********************************************");
    lastPackageHeaderDoc = thePackageDoc;
  }

}