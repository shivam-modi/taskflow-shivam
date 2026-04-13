package com.taskflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TaskflowApplication

fun main(args: Array<String>) {
    runApplication<TaskflowApplication>(*args)
}
