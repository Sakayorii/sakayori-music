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

-dontwarn kotlinx.serialization.internal.ClassValueReferences
-dontwarn org.slf4j.impl.StaticLoggerBinder
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
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

-keep class com.liskovsoft.** { *; }
-keep interface com.liskovsoft.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keep class com.liskovsoft.**
-keep class com.sakayori.kotlinytmusicscraper.** { *; }


-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

-keep class com.sakayori.data.di.loader.LoaderKt { *; }
-keep class com.sakayori.data.mapping.MappingKt { *; }
-keep class com.sakayori.data.extension.** { *; }

-keep class com.sakayori.kotlinytmusicscraper.extension.** { *; }
-keep class com.sakayori.kotlinytmusicscraper.models.** { *; }
-keep class com.sakayori.kotlinytmusicscraper.parser.** { *; }
-keep class com.sakayori.kotlinytmusicscraper.pages.** { *; }
-keep class com.sakayori.kotlinytmusicscraper.utils.** { *; }

-keep class com.sakayori.lyrics.parser.** { *; }
-keep class com.sakayori.lyrics.models.** { *; }
-keep class com.sakayori.lyrics.parser.** { *; }

-dontwarn com.sakayori.kotlinytmusicscraper.YouTube$SearchFilter$Companion
-dontwarn com.sakayori.kotlinytmusicscraper.YouTube$SearchFilter
-dontwarn com.sakayori.kotlinytmusicscraper.YouTube
-dontwarn com.sakayori.media3.di.Media3ServiceModuleKt
-dontwarn com.sakayori.media3.exoplayer.ExoPlayerAdapter
-dontwarn com.sakayori.spotify.Spotify
-dontwarn com.sakayori.spotify.model.response.spotify.CanvasResponse$Canvas$ThumbOfCanva
-dontwarn com.sakayori.spotify.model.response.spotify.CanvasResponse$Canvas
-dontwarn com.sakayori.spotify.model.response.spotify.CanvasResponse
-dontwarn com.sakayori.spotify.model.response.spotify.ClientTokenResponse$GrantedToken
-dontwarn com.sakayori.spotify.model.response.spotify.ClientTokenResponse
-dontwarn com.sakayori.spotify.model.response.spotify.PersonalTokenResponse
-dontwarn com.sakayori.spotify.model.response.spotify.SpotifyLyricsResponse$Lyrics$Line
-dontwarn com.sakayori.spotify.model.response.spotify.SpotifyLyricsResponse$Lyrics
-dontwarn com.sakayori.spotify.model.response.spotify.SpotifyLyricsResponse
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search$TracksV2$Items$Item$DataX$Duration
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search$TracksV2$Items$Item$DataX
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search$TracksV2$Items$Item
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search$TracksV2$Items
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search$TracksV2
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data$Search
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse$Data
-dontwarn com.sakayori.spotify.model.response.spotify.search.SpotifySearchResponse
-dontwarn com.sakayori.aiservice.AIHost
-dontwarn com.sakayori.aiservice.AiClient
-dontwarn com.sakayori.lyrics.SakayoriMusicLyricsClient
-dontwarn com.sakayori.lyrics.domain.Lyrics$LyricsX$Line
-dontwarn com.sakayori.lyrics.domain.Lyrics$LyricsX
-dontwarn com.sakayori.lyrics.domain.Lyrics

-keep class org.apache.commons.io.** { *; }

-keep class com.yausername.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-keepattributes SourceFile
