-keepclasseswithmembers class * {
    native <methods>;
}

-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    public *;
}

-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**
-dontnote io.ktor.**
-dontnote org.slf4j.**
-dontnote kotlinx.serialization.**

-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class uk.co.caprica.vlcj.** { *; }
-dontwarn uk.co.caprica.vlcj.**

-keep class javafx.** { *; }
-keep class com.sun.javafx.** { *; }
-dontwarn javafx.**
-dontwarn com.sun.javafx.**

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

-keep class nl.adaptivity.xmlutil.** { *; }
-dontwarn nl.adaptivity.xmlutil.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-keep class com.sakayori.domain.data.model.** { *; }
-keep class com.mohamedrejeb.ksoup.html.** { *; }
-keep class org.schabi.newpipe.extractor.downloader.** { *; }

-keep class org.koin.core.** { *; }
-dontwarn org.koin.**

-keep class kotlinx.coroutines.CoroutineExceptionHandler
-keep class kotlinx.coroutines.internal.MainDispatcherFactory
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontnote kotlinx.serialization.**

-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-keep class com.sakayori.music.data.model.** { *; }
-keep class com.sakayori.music.extension.AllExtKt { *; }
-keep class com.sakayori.music.extension.AllExtKt$* { *; }
-keep class com.sakayori.kotlinytmusicscraper.extension.MapExtKt$* { *; }

-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-keepattributes Signature, InnerClasses, EnclosingMethod

-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

-dontwarn javax.annotation.**

-dontwarn kotlin.Unit

-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn javax.annotation.**

-dontwarn org.codehaus.mojo.animal_sniffer.*

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.Util

-keep class com.liskovsoft.** { *; }
-keep interface com.liskovsoft.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keep class com.sakayori.kotlinytmusicscraper.** { *; }

-dontwarn javax.script.AbstractScriptEngine
-dontwarn javax.script.Bindings
-dontwarn javax.script.Compilable
-dontwarn javax.script.CompiledScript
-dontwarn javax.script.Invocable
-dontwarn javax.script.ScriptContext
-dontwarn javax.script.ScriptEngine
-dontwarn javax.script.ScriptEngineFactory
-dontwarn javax.script.ScriptException
-dontwarn javax.script.SimpleBindings
-dontwarn jdk.dynalink.CallSiteDescriptor
-dontwarn jdk.dynalink.DynamicLinker
-dontwarn jdk.dynalink.DynamicLinkerFactory
-dontwarn jdk.dynalink.NamedOperation
-dontwarn jdk.dynalink.Namespace
-dontwarn jdk.dynalink.NamespaceOperation
-dontwarn jdk.dynalink.Operation
-dontwarn jdk.dynalink.RelinkableCallSite
-dontwarn jdk.dynalink.StandardNamespace
-dontwarn jdk.dynalink.StandardOperation
-dontwarn jdk.dynalink.linker.GuardedInvocation
-dontwarn jdk.dynalink.linker.GuardingDynamicLinker
-dontwarn jdk.dynalink.linker.LinkRequest
-dontwarn jdk.dynalink.linker.LinkerServices
-dontwarn jdk.dynalink.linker.TypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.CompositeTypeBasedGuardingDynamicLinker
-dontwarn jdk.dynalink.linker.support.Guards
-dontwarn jdk.dynalink.support.ChainedCallSite

-keep class org.apache.commons.io.** { *; }

-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keepattributes SourceFile

-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

-dontwarn com.sakayori.data.di.loader.LoaderKt
-dontwarn com.sakayori.media3.ui.MediaPlayerViewKt

-keep class com.sakayori.data.di.loader.LoaderKt { *; }
-keep class com.sakayori.data.mapping.MappingKt { *; }
-keep class com.sakayori.data.extension.** { *; }
-keep class com.sakayori.data.di.** { *; }

-keep class com.sakayori.kotlinytmusicscraper.** { *; }

-keep class com.sakayori.lyrics.parser.** { *; }
-keep class com.sakayori.lyrics.models.** { *; }
-keep class org.simpmusic.nowplayingcenter.** { *; }
-keep class io.github.selemba1000.** { *; }
-keep class com.sakayori.lyrics.parser.** { *; }

-keep class org.freedesktop.dbus.** { *; }
-keep class com.github.hypfvieh.** { *; }
-dontwarn org.freedesktop.dbus.**
-dontwarn com.github.hypfvieh.**
-keepnames class org.freedesktop.dbus.spi.transport.ITransportProvider
-keep class * implements org.freedesktop.dbus.spi.transport.ITransportProvider { *; }
-adaptresourcefilecontents META-INF/services/**
-keepnames class * implements java.util.ServiceLoader$Provider

-keep class com.google.re2j.** { *; }
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.datastore.preferences.** { *; }

-dontwarn **
-dontwarn com.jetbrains.cef.remote.ThriftTransport**
-dontwarn org.apache.thrift.**
-dontwarn com.sun.jna.**
-dontwarn org.cef.**
-keep class org.apache.thrift.** { *; }
-keep class com.jetbrains.cef.** { *; }
-dontwarn **
-dontnote **
-keep class * { *; }
