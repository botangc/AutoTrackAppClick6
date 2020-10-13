package com.sensorsdata.analytics.android.plugin

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.regex.Matcher

/**
 * class文件处理器
 */
class SensorsAnalyticsClassModifier {
    private static HashSet<String> exclude = new HashSet<>();
    static {
        // 自定义过滤文件夹
        exclude = new HashSet<>()
        exclude.add('android.support')
        exclude.add('com.sensorsdata.analytics.android.sdk')
    }

    /**
     * 修改jar包文件
     *
     * @param jarFile jar包文件
     * @param tempDir 缓存文件
     * @param nameHex 文件路径是否转md5
     * @return 存储文件
     */
    static File modifyJar(File jarFile, File tempDir, boolean nameHex) {
        /**
         * 读取原 jar
         */
        def file = new JarFile(jarFile, false)

        /**
         * 设置输出到的 jar
         */
        def hexName = ""
        if (nameHex) {
            hexName = DigestUtils.md5Hex(jarFile.absolutePath).substring(0, 8)
        }
        // 文件路径前8位行程md5 拼接 文件原名
        def outputJar = new File(tempDir, hexName + jarFile.name)
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outputJar))
        Enumeration enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            // 获取每个部分的Entry，并生成输入流
            JarEntry jarEntry = (JarEntry) enumeration.nextElement()
            InputStream inputStream = null
            try {
                inputStream = file.getInputStream(jarEntry)
            } catch (Exception e) {
                return null
            }
            String entryName = jarEntry.getName()
            if (entryName.endsWith(".DSA") || entryName.endsWith(".SF")) {
                //ignore
            } else {
                try {
                    String className
                    JarEntry jarEntry2 = new JarEntry(entryName)
                    jarOutputStream.putNextEntry(jarEntry2)

                    byte[] modifiedClassBytes = null
                    byte[] sourceClassBytes = IOUtils.toByteArray(inputStream)
                    // 判断是否需要处理
                    if (entryName.endsWith(".class")) {
                        className = entryName.replace(Matcher.quoteReplacement(File.separator), ".").replace(".class", "")
                        if (isShouldModify(className)) {
                            modifiedClassBytes = modifyClass(sourceClassBytes)
                        }
                    }
                    if (modifiedClassBytes == null) {
                        modifiedClassBytes = sourceClassBytes
                    }
                    jarOutputStream.write(modifiedClassBytes)
                    jarOutputStream.closeEntry()
                    inputStream.close()
                } catch (Exception e) {

                }
            }
        }
        jarOutputStream.close()
        file.close()
        return outputJar
    }

    /**
     * 读取/修改class
     *
     * @param srcClass 原class的字节
     * @return 修改后的class字节
     * @throws IOException
     */
    private static byte[] modifyClass(byte[] srcClass) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        // 自定义的class文件处理器
        ClassVisitor classVisitor = new SensorsAnalyticsClassVisitor(classWriter)
        ClassReader cr = new ClassReader(srcClass)
        cr.accept(classVisitor, ClassReader.SKIP_FRAMES)
        return classWriter.toByteArray()
    }

    /**
     * 判断是否需要处理class文件
     *
     * @param className class文件名
     * @return true表示需要处理，false表示不处理（即过滤）
     */
    protected static boolean isShouldModify(String className) {
        Iterator<String> iterator = exclude.iterator()
        while (iterator.hasNext()) {
            String packageName = iterator.next()
            if (className.startsWith(packageName)) {
                return false
            }
        }

        if (className.contains('R$') ||
                className.contains('R2$') ||
                className.contains('R.class') ||
                className.contains('R2.class') ||
                className.contains('BuildConfig.class')) {
            return false
        }

        return true
    }

    /**
     * 处理class文件
     *
     * @param dir 源文件夹
     * @param classFile 源文件
     * @param tempDir 临时存储文件夹
     * @return 修改后的文件
     */
    static File modifyClassFile(File dir, File classFile, File tempDir) {
        File modified = null
        try {
            // 得到相对路径，并转化为class全称
            String className = path2ClassName(classFile.absolutePath.replace(dir.absolutePath + File.separator, ""))
            // 读取源文件，并插桩代码
            byte[] sourceClassBytes = IOUtils.toByteArray(new FileInputStream(classFile))
            byte[] modifiedClassBytes = modifyClass(sourceClassBytes)
            if (modifiedClassBytes) {
                // 将修改后的代码，存储到临时文件
                modified = new File(tempDir, className.replace('.', '') + '.class')
                if (modified.exists()) {
                    modified.delete()
                }
                modified.createNewFile()
                FileOutputStream fos = new FileOutputStream(modified)
                fos.write(modifiedClassBytes)
                fos.close()
            }
        } catch (Exception e) {
            e.printStackTrace()
            // 如果出错，则返回原文件
            modified = classFile
        }
        return modified
    }

    /**
     * 将文件路径转化为class全名，分隔符替换为'.'，并去掉后缀名
     *
     * @param pathName 文件路径
     * @return class全称
     */
    static String path2ClassName(String pathName) {
        pathName.replace(File.separator, ".").replace(".class", "")
    }
}