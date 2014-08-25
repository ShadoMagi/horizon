sbt-build-utilities
===================

Current Version: 0.7.0

[View the ScalaDocs](https://github.paypal.com/pages/Paypal-Commons-R/sbt-build-utilities/api/0.7.0/index.html#com.paypal.stingray.sbt.package)

The Build Utilities plugin was designed so common build settings can be imported via an sbt plugin rather than having to copy code in every new project's build file.

## How to Include In Project

In **project/plugins.sbt**, add:

`addSbtPlugin("com.paypal.stingray" % "sbt-build-utilities" % "0.7.0")`

In **project/Build.scala**, add:

`import com.paypal.stingray.sbt.BuildUtilities._`

For details on what's included with this plugin, see wiki:

https://confluence.paypal.com/cnfl/display/stingray/Stingray+Build+Utilities