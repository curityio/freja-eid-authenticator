Verisec Freja eID Authenticator Plugin
======================================
.. image:: https://travis-ci.org/curityio/verisec-authenticator.svg?branch=master
    :target: https://travis-ci.org/curityio/verisec-authenticator

This project provides an opens source Verisec Authenticator plug-in for the Curity Identity Server. This allows an administrator to add functionality to Curity which will then enable end users to login using their Verisec credentials. The app that integrates with Curity may also be configured to receive the Verisec access token and refresh token, allowing it to manage resources in Verisec.


Building the Plugin
~~~~~~~~~~~~~~~~~~~

You can build the plugin by issue the command ``mvn package``. This will produce a JAR file in the ``target`` directory, which can be installed.

Installing the Plugin
~~~~~~~~~~~~~~~~~~~~~

To install the plugin, copy the compiled JAR (and all of its dependencies) into the ``${IDSVR_HOME}/usr/share/plugins/${pluginGroup}`` on each node, including the admin node. For more information about installing plugins, refer to the `curity.io/plugins`_.

Required Dependencies
"""""""""""""""""""""

For a list of the dependencies and their versions, run ``mvn dependency:list``. Ensure that all of these are installed in the plugin group; otherwise, they will not be accessible to this plug-in and run-time errors will result.


Run Mock Node Server
""""""""""""""""""""
In order to run tests you need to run mock node server which will act as Freja e-id server. Execute following command to start mock node server.

``docker-compose up``

More Information
~~~~~~~~~~~~~~~~

Please visit `curity.io`_ for more information about the Curity Identity Server.

.. _curity.io/plugins: https://support.curity.io/docs/latest/developer-guide/plugins/index.html#plugin-installation
.. _curity.io: https://curity.io/
