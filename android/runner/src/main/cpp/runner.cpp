#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstdio>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_kr_compile_runner_GuiRunnerActivity_setWorkingDirectory(JNIEnv* env, jclass, jstring directory) {
    const char* chars = env->GetStringUTFChars(directory, nullptr);
    if (!chars) return JNI_FALSE;
    const int result = chdir(chars);
    env->ReleaseStringUTFChars(directory, chars);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_kr_compile_runner_RunnerActivity_execute(JNIEnv* env, jclass, jstring output_file,
                                              jstring library_file, jstring working_directory) {
    const char* chars = env->GetStringUTFChars(output_file, nullptr);
    if (!chars) return 125;
    std::string path(chars);
    env->ReleaseStringUTFChars(output_file, chars);
    const char* library_chars = env->GetStringUTFChars(library_file, nullptr);
    if (!library_chars) return 125;
    std::string library_path(library_chars);
    env->ReleaseStringUTFChars(library_file, library_chars);
    const char* working_chars = env->GetStringUTFChars(working_directory, nullptr);
    if (!working_chars) return 125;
    std::string working_path(working_chars);
    env->ReleaseStringUTFChars(working_directory, working_chars);
    int file = open(path.c_str(), O_CREAT | O_TRUNC | O_WRONLY, 0600);
    if (file < 0) return 124;
    int saved_out = dup(STDOUT_FILENO), saved_error = dup(STDERR_FILENO);
    dup2(file, STDOUT_FILENO); dup2(file, STDERR_FILENO); close(file);
    if (chdir(working_path.c_str()) != 0) {
        std::string message = "Laufzeitordner kann nicht geöffnet werden: " + working_path + "\n";
        write(STDERR_FILENO, message.data(), message.size());
        dup2(saved_out, STDOUT_FILENO); dup2(saved_error, STDERR_FILENO);
        close(saved_out); close(saved_error);
        return 123;
    }
    using Main = int (*)(int, char**);
    void* user_library = dlopen(library_path.c_str(), RTLD_NOW | RTLD_LOCAL);
    auto main_function = user_library ? reinterpret_cast<Main>(dlsym(user_library, "main")) : nullptr;
    using LanguageMain = int (*)();
    auto language_main = user_library ? reinterpret_cast<LanguageMain>(dlsym(user_library, "KrLanguageMain")) : nullptr;
    int result = 127;
    if (main_function) {
        char name[] = "kr-program"; char* argv[] = {name, nullptr};
        result = main_function(1, argv);
        std::fflush(nullptr);
    } else if (language_main) {
        result = language_main();
        std::fflush(nullptr);
    } else {
        const char* detail = dlerror();
        std::string message = "C++-Programm kann nicht geladen werden: ";
        message += detail ? detail : "kein exportierter main()-Einstieg gefunden";
        message += '\n';
        write(STDERR_FILENO, message.data(), message.size());
    }
    dup2(saved_out, STDOUT_FILENO); dup2(saved_error, STDERR_FILENO);
    close(saved_out); close(saved_error);
    if (user_library) dlclose(user_library);
    return result;
}
