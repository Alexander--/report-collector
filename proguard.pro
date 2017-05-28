-optimizationpasses 5
-allowaccessmodification
-useuniqueclassmembernames
#-overloadaggressively

# Better safe then sorry
-dontskipnonpubliclibraryclassmembers
-dontskipnonpubliclibraryclasses

# Proguard, you piece of shit, don't optimize resources, referenced by absolute paths!
#-adaptresourcefilenames
#-adaptresourcefilecontents

-repackageclasses 'xfd'

# Making obfuscated stacktraces useful
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Jet optimizer does that for us and hopefully have better leverage at it
#-optimizations "!method/inlining/short"

# registered in META-INF/services; todo: make it work without this
-keep class com.mysql.cj.jdbc.Driver { *; }

# causes VerifyError (Excelsior bug?)
#-keep class org.tmatesoft.sqljet.core.internal.table.SqlJetBtreeDataTable { *; }

# causes ClassFormatError (Excelsior bug?)
-keep class org.tmatesoft.sqljet.core.schema.ISqlJetForeignKey { *; }
-keep class org.tmatesoft.sqljet.core.schema.ISqlJetInExpression { *; }

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,*Annotation*,EnclosingMethod

-outjars build/proguard/merged.jar

-printmapping mapping.txt

-dontwarn org.antlr.runtime.tree.**
-dontwarn org.hsqldb.server.**
-dontwarn org.hsqldb.util.**
-dontwarn com.sun.**
-dontwarn javax.xml.**
-dontwarn com.mysql.cj.core.log.Slf4JLogger
-dontwarn com.mysql.cj.jdbc.integration.**
-dontwarn com.mysql.cj.x.**
-dontwarn com.mysql.cj.api.x.**
-dontwarn com.mysql.cj.xdevapi.**

# mysql locates loggers via reflection
-keep class * extends com.mysql.cj.api.log.Log { *; }

-keepclasseswithmembernames,includedescriptorclasses class ** {
    native <methods>;
}

# Removing no-arg constructor would prevent framework from dynamically
# instantiating classes
-keepclassmembers class ** {
    !protected !private <init>();
}

# stop Proguard from optimizing enums (shitty Java devs, and their shitty reflection habits!)
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# some mysql driver code loves to throw exceptions reflectively
-keepclasseswithmembers,allowoptimization class ** extends java.lang.Exception { public <init>(java.lang.String); }
-keep,allowshrinking,allowoptimization class ** extends java.lang.Exception

# uses reflection to load custom socket factory, we use default one
# also uses reflection to mess HostnameChecker (but has safe fallback)
-dontnote com.sun.mail.util.SocketFetcher

# uses reflection to load custom content-type handlers, we don't use those
-dontnote com.sun.mail.util.MimeUtil

# !org.tmatesoft.sqljet.core.**,
-keep class net.sf.xfd.server.Main { void main(java.lang.String[]); }
-keep class com.sun.mail.handlers.** { *; }
-keep class !org.antlr.**,!net.sf.xfd.**,!com.sun.mail.imap.**,!com.sun.mail.pop3.**,!com.mysql.**,** { *; }
-keep interface !org.antlr.**,!org.tmatesoft.sqljet.core.**,!net.sf.xfd.**,!com.sun.mail.imap.**,!com.sun.mail.pop3.**,!com.mysql.**,** { *; }
-keep enum !org.antlr.**,!org.tmatesoft.sqljet.core.**,!net.sf.xfd.**,!com.sun.mail.imap.**,!com.sun.mail.pop3.**,!com.mysql.**,** { *; }