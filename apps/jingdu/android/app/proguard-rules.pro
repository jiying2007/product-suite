-keep class com.junchen.jingdu.NativeCore { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Protobuf Lite resolves generated message fields (for example ReaderSettingsProto.schema_)
# reflectively from RawMessageInfo. R8 must not rename/shrink those generated members or the
# production-minified build crashes in MessageSchema before Reader can launch.
# Scope this to Jingdu's generated proto package instead of disabling shrinking globally.
-keep class com.junchen.jingdu.proto.** extends com.google.protobuf.GeneratedMessageLite { *; }

# Hosted Macrobenchmark-only entry point. The class exists only in src/benchmark, so this
# keep rule cannot add benchmark controls to the production release source set.
-keep class com.junchen.jingdu.ReaderBenchmarkFixtureProvider { *; }
