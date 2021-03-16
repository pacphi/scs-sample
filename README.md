# Spring Cloud Config Service Sample

This application can be used to test Spring Cloud Config service connectivity by deploying it to a Cloud Foundry platform that has the Spring Cloud Services tile installed.

The demo application includes a deployment manifest that makes it simple to deploy the application. 

## Prerequisites

Required

* Access to a foundation with Pivotal Application Service 2.6 or better installed
  * and [Pivotal Application Service](https://pivotal.io/platform/pivotal-application-service) account credentials
    * e.g., an account on [PCFOne](https://apps.run.pcfone.io/) should suffice 
* Targeted foundation should have the [Spring Cloud Services](https://docs.pivotal.io/spring-cloud-services/3-1/common/index.html) service tile installed and the service enabled in the `cf marketplace`
* The [spring-cloud-services](https://github.com/pivotal-cf/spring-cloud-services-cli-plugin#installing) CF plugin installed

## Tools

* [git](https://git-scm.com/downloads) 2.20.1 or better
* [JDK](http://openjdk.java.net/install/) 8 or better
* [cf](https://docs.cloudfoundry.org/cf-cli/install-go-cli.html) CLI 6.41.0 or better


## Clone

```
git clone https://github.com/pacphi/scs-sample.git
```

## Build

```bash
cd scs-sample
../gradlew assemble
```

## Authenticate

If you're using PCFOne to demo, then

```bash
cf api api.run.pcfone.io
cf login --sso
```
> Follow the link and authenticate via Workspace One.  Use the token returned as your password.

## Simple Deployment

Target an organization and space.

```bash
cf target -o {org} -s {space}
```

Deploy

```bash
cf push --no-start
```
Be aware that you may have to change the application name.  You can override the one supplied in the manifest by adding a name, like so

```bash
cf push my-stuff --no-start
```
> If you choose to do this, then make sure you replace all occurrences of `stuff` below with the name you specified.


Create a Spring Cloud Config service instance and seed with initial configuration

```bash
cf create-service p.config-server standard mycs
cf config-server-add-credhub-secret mycs \
  stuff/blue/master/stuff '{ "clouds": [ "Alibaba", "Digital Ocean", "Oracle" ], "languages": [ "Java", "Clojure", "PHP", "Rust"  ] }'
```

Note we are adding a secret `stuff/blue/master/stuff` which acts as a name space for key-value pairs in JSON.  The name space consists of a `spring.application.name`, a `profile` activated in `spring.profiles.active`, a `label`, usually specifying a Git repository branch, and a `secret name`, usually the value of the prefix in a [@ConfigurationProperties](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/context/properties/ConfigurationProperties.html) annotated model class (which defaults to the simple name of the class if no prefix attribute specified in the annotation). See [Stuff.java](src/main/java/io/pivotal/scs/sample/Stuff.java).


Bind service instance to application, set the active profiles, trust certs, then startup the application

```bash
cf bind-service stuff mycs
cf set-env stuff SPRING_PROFILES_ACTIVE cloud,credhub,blue
cf set-env stuff TRUST_CERTS api.run.pcfone.io
cf start stuff
```

### Test the application

If you have `curl`, then

```bash
curl -k https://stuff.apps.pcfone.io
```

Better yet, with [httpie](https://httpie.io/)

```
http https://stuff.apps.pcfone.io
```

### Update configuration in service instance and restage application

Edit the secret. In this case, we are removing then re-adding the secret above, but with a different JSON payload.

```bash
cf config-server-remove-credhub-secret mycs stuff/blue/master/stuff
cf config-server-add-credhub-secret mycs \
  stuff/blue/master/stuff '{ "clouds": [ "AWS", "Azure", "GCP"  ], "languages": [ "Javascript", "Go", "Kotlin", "Python"  ] }'
cf restage stuff
```
> Note we'll take down time while we do this.  We'll explore how to update configuration in a zero-down time fashion later on.

### Test the application again to see that the updates took effect

```bash
http https://stuff.apps.pcfone.io
```

### Teardown the application and service instance

```bash
cf unbind-service stuff mycs
cf delete-service -f mycs
cf delete -r -f stuff
```

## Blue-green deployment

Let's create a shared Spring Cloud Config Server service instance.

```bash
cf create-service p.config-server standard sccs
```

Let's start with pushing a `blue-version` of the application, stamp it `blue`, add a secret to the service instance, bind it to the application and start the application.

```bash
cf push stuff-blue --random-route --no-start
cf set-env stuff-blue VERSION blue
cf set-env stuff-blue SPRING_PROFILES_ACTIVE cloud,credhub,blue
cf set-env stuff-blue TRUST_CERTS api.run.pcfone.io
cf bind-service stuff-blue sccs
cf config-server-add-credhub-secret sccs \
  stuff/blue/master/stuff '{ "clouds": [ "Alibaba", "Digital Ocean", "Oracle" ], "languages": [ "Java", "Clojure", "PHP", "Rust"  ] }'
cf start stuff-blue
```

Next, we will map a shared host name `stuff` onto the same domain as the `blue-version` of the application as this will be the host name we will want users to visit to interact with the application.

```bash
BLUE_DOMAIN=$(cf app stuff-blue | grep routes | awk {'print $2'} | cut -d'.' -f 2,3,4,5,6)
echo $BLUE_DOMAIN
cf map-route stuff-blue $BLUE_DOMAIN --hostname stuff
```

Let's test that the result of transacting the shared host name and domain matches what we expect in [stuff-blue.json](stuff-blue.json).

```bash
http https://stuff.apps.pcfone.io
```

Let's pretend that we've made an update to the implementation and/or the configuration and we want to roll-out the update without disruption.  We'll push a `green-version` of the application, stamp it `green`, reuse the `sccs` service instance, add a secret, bind the service instance to the application and start the application.

```bash
cf push stuff-green --random-route --no-start
cf set-env stuff-green VERSION green
cf set-env stuff-green SPRING_PROFILES_ACTIVE cloud,credhub,green
cf set-env stuff-green TRUST_CERTS api.run.pcfone.io
cf config-server-add-credhub-secret sccs \
  stuff/green/master/stuff '{ "clouds": [ "AWS", "Azure", "GCP"  ], "languages": [ "Javascript", "Go", "Kotlin", "Python"  ] }'
cf bind-service stuff-green sccs
cf start stuff-green
```

Next, let's map the aforementioned shared host name on the `green-version` of the application.  When we do this, both applications will take on traffic at the shared host name and domain. Requests will be serviced in a random fashion from each versioned application.

```bash
GREEN_DOMAIN=$(cf app stuff-green | grep routes | awk {'print $2'} | cut -d'.' -f 2,3,4,5,6)
echo $GREEN_DOMAIN
cf map-route stuff-green $GREEN_DOMAIN --hostname stuff
```

To complete the cut over to the `green-version` of the application (and configuration) we will `unmap` the shared host name from the `blue-version` of the application (and configuration).

```bash
cf unmap-route stuff-blue $BLUE_DOMAIN --hostname stuff
```

Let's test that the result of transacting the shared host name and domain matches what we expect in [stuff-green.json](stuff-green.json).

```bash
http https://stuff.apps.pcfone.io
```

At this point the `blue-version` of the application will no longer take on new requests at the shared host name and domain.  You may decide to tear it down or roll back if there are noted issues with the `green-version` of the application (and configuration).  Rolling back is as simple as re-mapping the shared host name to the `blue-version` of the application with the `cf map-route` command above. Then you could `cf unmap-route` the `green-version` of the application.  Tearing down the `blue-version` is accomplished with:

```bash
cf unbind-service stuff-blue sccs
cf delete -r -f stuff-blue
```

Note: if you don't want to allow direct public access to either the blue or green versions of the application you could swap `--random-route` with `--no-route`. However, the method we use above to obtain the domain would have to change.  (But then you should already know the domain, shouldn't you, for the target foundation in your operating environment?)
 
## Credits

* [Managing secrets with Credhub](https://docs.pivotal.io/spring-cloud-services/3-1/common/config-server/managing-secrets-with-credhub.html)
* [The cook repo](https://github.com/spring-cloud-services-samples/cook)
* [cf update-service](https://cli.cloudfoundry.org/en-US/v6/update-service.html)
* [cf map-route](https://cli.cloudfoundry.org/en-US/v6/map-route.html)
* [cf unmap-route](https://cli.cloudfoundry.org/en-US/v6/unmap-route.html)
* [CloudFoundry: Using Blue-Green deployment and route mapping for continuous deployment](https://fabianlee.org/2018/02/20/cloudfoundry-using-blue-green-deloyment-and-route-mapping-for-continuous-deployment/)
* [Changes in Spring Cloud 2020.0.1 requiring addition of spring-cloud-starter-bootstrap](https://stackoverflow.com/questions/30016868/spring-cloud-config-client-not-loading-configuration-from-config-server)
* [Disabling Webfux Security](https://stackoverflow.com/questions/53002900/disable-webflux-security/55356678)

