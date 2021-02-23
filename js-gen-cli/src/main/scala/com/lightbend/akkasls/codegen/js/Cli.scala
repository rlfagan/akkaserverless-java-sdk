/*
 * Copyright (c) Lightbend Inc. 2021
 *
 */

package com.lightbend.akkasls.codegen
package js

import scopt.OParser

import java.io.File
import java.nio.file.{ Path, Paths }

object Cli {

  private final val CWD = Paths.get(".").toAbsolutePath

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  private case class Config(
      baseDir: Path = CWD,
      indexFile: String = "index.js",
      descriptorSetOutputDirectory: Path = CWD,
      descriptorSetFileName: String = "user-function.desc",
      serviceNamesFilter: String = ".*ServiceEntity",
      sourceDirectory: Path = CWD,
      testSourceDirectory: Path = CWD
  )

  private val builder = OParser.builder[Config]
  private val parser = {
    import builder._
    OParser.sequence(
      programName("akkasls-codegen-js"),
      head("akkasls-codegen-js", BuildInfo.version),
      opt[File]("base-dir")
        .action((x, c) => c.copy(baseDir = x.toPath.toAbsolutePath))
        .text(
          "The base directory for the project as a parent to the source files - defaults to the current working directory"
        ),
      opt[String]("main-file")
        .action((x, c) => c.copy(indexFile = x))
        .text(
          "The name of the file to be used to set up entities, relative to the source directory - defaults to index.js"
        ),
      opt[File]("descriptor-set-output-dir")
        .action((x, c) => c.copy(descriptorSetOutputDirectory = x.toPath.toAbsolutePath))
        .text(
          "The location of the descriptor output file generated by protoc - defaults to the current working directory"
        ),
      opt[String]("descriptor-set-file")
        .action((x, c) => c.copy(descriptorSetFileName = x))
        .text(
          "The name of the descriptor output file generated by protoc - defaults to user-function.desc"
        ),
      opt[String]("service-names-filter")
        .action((x, c) => c.copy(serviceNamesFilter = x))
        .text(
          "The regex pattern used to discern entities from service declarations - defaults to .*ServiceEntity"
        ),
      opt[File]("source-dir")
        .action((x, c) => c.copy(sourceDirectory = x.toPath.toAbsolutePath))
        .text(
          "The location of source files in relation to the base directory - defaults to the current working directory"
        ),
      opt[File]("test-source-dir")
        .action((x, c) => c.copy(testSourceDirectory = x.toPath.toAbsolutePath))
        .text(
          "The location of test source files in relation to the base directory - defaults to the current working directory"
        ),
      help("help").text("Prints this usage text")
    )
  }

  def main(args: Array[String]): Unit =
    OParser.parse(parser, args, Config()) match {
      case Some(config) =>
        val protobufDescriptor =
          config.descriptorSetOutputDirectory.resolve(config.descriptorSetFileName).toFile
        if (protobufDescriptor.exists) {
          println("Inspecting proto file descriptor for entity generation...")
          val _ = DescriptorSet.fileDescriptors(protobufDescriptor) match {
            case Right(fileDescriptors) =>
              fileDescriptors.foreach {
                case Right(fileDescriptor) =>
                  val entities =
                    ModelBuilder.introspectProtobufClasses(
                      fileDescriptor,
                      config.serviceNamesFilter
                    )

                  js.SourceGenerator
                    .generate(
                      protobufDescriptor.toPath,
                      entities,
                      config.sourceDirectory,
                      config.testSourceDirectory,
                      config.indexFile
                    )
                    .foreach { p =>
                      println("Generated: " + config.baseDir.relativize(p.toAbsolutePath).toString)
                    }
                case Left(e) =>
                  System.err.println(
                    "There was a problem building the file descriptor from its protobuf:"
                  )
                  System.err.println(e.toString)
                  sys.exit(1)
              }
            case Left(DescriptorSet.CannotOpen(e)) =>
              System.err.println(
                "There was a problem opening the protobuf descriptor file:"
              )
              System.err.println(e.toString)
              sys.exit(1)
          }

        } else {
          println("Skipping generation because there is no protobuf descriptor found.")
        }
      case _ =>
        sys.exit(1)
    }
}
