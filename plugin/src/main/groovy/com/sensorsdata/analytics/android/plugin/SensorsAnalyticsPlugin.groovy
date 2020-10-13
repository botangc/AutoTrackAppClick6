package com.sensorsdata.analytics.android.plugin

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 新增自定义插件Plugin，可注册自定义的Transform，在.class -> .dex时，先行遍历class文件，并在其中插装代码
 */
class SensorsAnalyticsPlugin implements Plugin<Project> {
    void apply(Project project) {
        // 自定义Task，对应的对象为SensorsAnalyticsExtension
        SensorsAnalyticsExtension extension = project.extensions.create("sensorsAnalytics", SensorsAnalyticsExtension)

        boolean disableSensorsAnalyticsPlugin = false
        Properties properties = new Properties()
        // 加载gradle.properties，并读取指定属性，判断是否添加Plugin
        if (project.rootProject.file('gradle.properties').exists()) {
            properties.load(project.rootProject.file('gradle.properties').newDataInputStream())
            disableSensorsAnalyticsPlugin = Boolean.parseBoolean(properties.getProperty("sensorsAnalytics.disablePlugin", "false"))
        }

        if (!disableSensorsAnalyticsPlugin) {
            AppExtension appExtension = project.extensions.findByType(AppExtension.class)
            appExtension.registerTransform(new SensorsAnalyticsTransform(extension))
        } else {
            println("------------您已关闭了插件--------------")
        }
    }
}