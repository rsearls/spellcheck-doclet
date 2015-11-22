# spellcheck-doclet
A clone of softframworks' SpellCheckDoclet. A copy of jazzy-0.5.2.  And a new version of 
SpellCheckDoclet with enhancements.

Both SpellCheckDoclet and jazzy are very old projects that have inactive for many years.
The project code for both has been archived on sourceforge.  It is unknown how much longer the 
code will be available.  In addition, neither project is buildable as a maven archive.  In order
to guarantee access to these APIs for the enhanced version of SpellCheckDoclet, the source code 
for both projects are provided in this project.  Both SpellCheckDoclet and jazzy have been made
buildable as maven archives.

softframworks' version of **spellcheck.SpellCheckDoclet** is unaltered and referenceable from SpellCheckDoclet-\<VERSION\>.jar
The (cloned) enhanced version, **org.jboss.SpellCheckDoclet**, is also referenceable from SpellCheckDoclet-\<VERSION\>.jar
 
 
org.jboss.SpellCheckDoclet supports all the options provided in softframworks' SpellCheckDoclet, see 
http://www.softframeworks.com/download/download.php and the follow 2 options.
 
*-reportfile <filename>    Write the results to the specified file.  When no file is specified results are written to standard out.
                            
*-unknownwords <filename>  A file of words not found in the dictionaries specified.
 
*An English dictionary, en.txt, of 119773 words is provided in the SpellCheckDoclet-<VERSION>.jar  It can be referenced by the input option, -dictionary dictionary/en.txt
 
 
#### Example configuration section of SpellCheckDoclet in the maven-javadoc-plugin
```
     <configuration>
        <doclet>org.jboss.spellcheck.SpellCheckDoclet</doclet>
        <docletArtifact>
            <groupId>spellcheck</groupId>
            <artifactId>SpellCheckDoclet</artifactId>
            <version>1.0</version>
        </docletArtifact> 
        <additionalparam>-dictionary dictionary/en.txt -dictionary ${project.parent.basedir}/en-custom.txt -reportfile spellCheckReport.txt -unknownwords unknownWords.txt</additionalparam>             
        <useStandardDocletOptions>false</useStandardDocletOptions>
        <outputDirectory>${project.build.directory}</outputDirectory>
        <destDir>spellcheck</destDir>
    </configuration>
```              
 
 
## References
 1 http://www.softframeworks.com/download/download.php 
 
 2 http://www.softframeworks.com/etc/spellcheck/SpellCheckDoclet.html 
   
 3 http://sourceforge.net/projects/jazzy
    