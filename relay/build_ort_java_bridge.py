"""Build ORT 1.28.0 Java JNI against sherpa-onnx's ORT 1.28.2 (API 28).
No duplicate ORT runtime, pickFirst, binary patch or private API.
Inputs: official v1.28.0 source + Maven Android AAR, NDK r27d, sherpa AAR.
"""
import argparse,pathlib,subprocess,zipfile,tempfile
p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--ndk',required=True);p.add_argument('--ort-aar',required=True);p.add_argument('--sherpa-aar',required=True);p.add_argument('--android-jar',required=True);p.add_argument('--java-home',required=True);p.add_argument('--output',required=True);a=p.parse_args()
source=pathlib.Path(a.source).resolve();ndk=pathlib.Path(a.ndk).resolve();java=pathlib.Path(a.java_home).resolve()
with tempfile.TemporaryDirectory() as tmp:
    tmp=pathlib.Path(tmp); headers=tmp/'headers';headers.mkdir();classes=tmp/'classes';classes.mkdir()
    subprocess.run([str(java/'bin/javac'),'-cp',str(pathlib.Path(a.android_jar).resolve()),'-d',str(classes),'-h',str(headers),*map(str,(source/'java/src/main/java').rglob('*.java')),*map(str,(source/'java/src/main/android').rglob('*.java'))],check=True)
    (headers/'onnxruntime_config.h').write_text('#pragma once\n#define ORT_VERSION "1.28.0"\n')
    native=source/'java/src/main/native'
    sources=[str(f) for f in native.glob('*.c') if 'OrtTrainingSession' not in f.name]
    artifacts=[]
    with zipfile.ZipFile(a.sherpa_aar) as sherpa:
        for abi,target in [('arm64-v8a','aarch64-linux-android26'),('x86_64','x86_64-linux-android26')]:
            lib=tmp/abi;lib.mkdir();(lib/'libonnxruntime.so').write_bytes(sherpa.read(f'jni/{abi}/libonnxruntime.so'))
            output=lib/'libonnxruntime4j_jni.so'
            subprocess.run([str(ndk/'toolchains/llvm/prebuilt/darwin-x86_64/bin'/f'{target}-clang'),'-shared','-fPIC','-O2','-Wl,-z,max-page-size=16384','-Wl,-z,defs','-I'+str(headers),'-I'+str(source/'include'),'-I'+str(source/'include/onnxruntime/core/session'),'-I'+str(native),*sources,'-L'+str(lib),'-lonnxruntime','-ldl','-llog','-o',str(output)],check=True)
            artifacts.append((f'jni/{abi}/libonnxruntime4j_jni.so',output.read_bytes()))
    with zipfile.ZipFile(a.ort_aar) as original, zipfile.ZipFile(a.output,'w',compression=zipfile.ZIP_DEFLATED) as output:
        for name in ['AndroidManifest.xml','classes.jar','R.txt','META-INF/com/android/build/gradle/aar-metadata.properties']: output.writestr(name,original.read(name))
        output.writestr('META-INF/LICENSE-ORT.txt',(source/'LICENSE').read_bytes())
        for name,data in artifacts: output.writestr(name,data)
print(a.output)
