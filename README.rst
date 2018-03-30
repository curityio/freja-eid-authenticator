Freja eID Authenticator Plugin
==============================
.. image:: https://travis-ci.org/curityio/freja-eid-authenticator.svg?branch=master
    :target: https://travis-ci.org/curityio/freja-eid-authenticator

This project provides an opens source Freja eID Authenticator plug-in for the Curity Identity Server. This allows an administrator to add functionality to Curity which will then enable end users to login using their Freja eID credentials. The app that integrates with Curity may also be configured to receive the Freja eID access token and refresh token, allowing it to manage resources in Freja eID.



Building the Plugin
~~~~~~~~~~~~~~~~~~~

You can build the plugin by issue the command ``mvn package``. This will produce a JAR file in the ``target`` directory, which can be installed.

Installing the Plugin
~~~~~~~~~~~~~~~~~~~~~

To install the plugin, copy the compiled JAR (and all of its dependencies) into the ``${IDSVR_HOME}/usr/share/plugins/${pluginGroup}`` on each node, including the admin node. For more information about installing plugins, refer to the `curity.io/plugins`_.


Creating a Freja eID Authenticator in Curity
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Configuration using the Admin GUI
"""""""""""""""""""""""""""""""""

To configure a new Freja eID authenticator using the Curity admin UI, do the following after logging in:

1. Go to the ``Authenticators`` page of the authentication profile wherein the authenticator instance should be created.
2. Click the ``New Authenticator`` button.
3. Enter a name (e.g., ``freja-eid1``).
4. For the type, pick the ``Freja eID`` option.
5. On the next page, you can define all of the standard authenticator configuration options like any previous authenticator that should run, the resulting ACR, transformers that should executed, etc. At the bottom of the configuration page, the Freja eID-specific options can be found.

    .. figure:: docs/images/freja-eid-authenticator-type-in-curity.png
        :align: center
        :width: 600px

        .. note::

        The Freja eID-specific configuration is generated dynamically based on the `configuration model defined in the Java interface <https://github.com/curityio/freja-eid-authenticator/blob/master/src/main/java/io/curity/identityserver/plugin/freja-eid/config/FrejaEidAuthenticatorPluginConfig.java>`_.


6. Certain required and optional configuration settings may be provided. One of these required settings is the ``HTTP Client`` setting. This is the HTTP client that will be used to communicate with the Freja eID OAuth server.
   You need to configure a ``Client SSL Keystore`` and a ``Server Trust Store`` before you create a Http Client.

Create Client SSL Keystore
""""""""""""""""""""""""""
    A. Click the ``Facilities`` button at the top-right of the screen.
    B. Next to ``Client SSL Keys`` under ``Crypto``, click ``New``.
    C. Enter some name (e.g., ``freja-eid-clientSSLKeystore``).
    D. Select ``Upload Existing`` and click ``Next``.
    E. Upload the ``Keystore`` and enter its ``password`` with an optional ``alias``.
    F. Click ``Add & Commit``.

    .. figure:: docs/images/create-client-ssl-keystore1.png
        :align: center
        :width: 600px


    .. figure:: docs/images/create-client-ssl-keystore2.png
        :align: center
        :width: 600px

Create Server Trust Store
"""""""""""""""""""""""""
    A. Click the ``Facilities`` button at the top-right of the screen.
    B. Next to ``Server Trust Stores`` under ``Crypto``, click ``New``.
    C. Enter some name (e.g., ``frejaEidTrustStore``).
    D. Upload ``Public key file``.
    E. Click ``Add``.

    .. figure:: docs/images/create-server-truststore.png
        :align: center
        :width: 600px

Create Http Client
""""""""""""""""""
    A. Click the ``Facilities`` button at the top-right of the screen.
    B. Next to ``HTTP``, click ``New``.
    C. Enter some name (e.g., ``freja-eidClient``).
    D. Enable ``Use Truststore`` toggle button.
    E. Select the ``Client Keystore`` from dropdown, the one you just created in above steps.
    F. Click ``Apply``.

    .. figure:: docs/images/create-http-client.png
        :align: center
        :width: 600px

7. Back in the Freja eID authenticator instance that you started to define, select the new HTTP client from the dropdown.

    .. figure:: docs/images/configure-http-client.png
        :align: center
        :width: 400px


8. Select the ``Environment`` to use, either ``Production`` or ``Pre Production``.
9. Select the ``User Info Type`` from dropdown. It has ``Email`` or ``SSN`` as the allowed options. ``SSN`` corresponds to ``Username``.

Once all of these changes are made, they will be staged, but not committed (i.e., not running). To make them active, click the ``Commit`` menu option in the ``Changes`` menu. Optionally enter a comment in the ``Deploy Changes`` dialogue and click ``OK``.

Once the configuration is committed and running, the authenticator can be used like any other.


Testing Instructions
""""""""""""""""""""
To test the plugin in ``Pre Production`` environment, follow the below instructions.

1. Download app from ``AppStore`` or ``PlayStore``.
2. Start the app in ``Test Mode`` by following instructions from Verisec.
3. Activate your ID by entering your email and confirming it.
4. Now you can use this email for testing.
5. In order to use ``SSN`` for testing, you need to vet your ID first
6. Upgrade your account from mobile app
7. Vet your ID from https://vettingportal.test.frejaeid.com
8. Now you can use your ``SSN`` for testing.

Note :: You can find detailed instructions from documentation provided by Verisec, the document name will be something like : ``Freja eID Relying Party Documentation - Testing instructions.zip``.


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
