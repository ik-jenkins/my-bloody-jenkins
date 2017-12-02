import org.yaml.snakeyaml.Yaml

handler = 'Clouds'
configHandler = evaluate(new File("/usr/share/jenkins/config-handlers/${handler}Config.groovy"))

def assertCloud(id, type, closure){
    def cloud = jenkins.model.Jenkins.instance.clouds.find{it.name == id}
    assert type.isInstance(cloud) : "Cloud ${id} is not instanceof ${type}"
    if(closure){
        closure(cloud)
    }
}

def testEcs(){
	def config = new Yaml().load("""
ecs-cloud:
  type: ecs
  credentialsId: aws-cred
  region: us-east-1
  cluster: ecs-cluster
  connectTimeout: 60
  jenkinsUrl: http://127.0.0.1:8080
  tunnel: 127.0.0.1:8080
  templates:
    - name: ecs-template
      labels:
        - test
        - generic
      image: odavid/jenkins-jnlp-slave:latest
      remoteFs: /home/jenkins
      memory: 4000
      memoryReservation: 2000
      cpu: 512
      jvmArgs: -Xmx1G
      entrypoint: /entrypoint.sh
      logDriver: aws
      dns: 8.8.8.8
      privileged: true
      logDriverOptions:
        optionA: optionAValue
        optionB: optionBValue
      environment:
        ENV1: env1Value
        ENV2: env2Value
      extraHosts:
        extrHost1: extrHost1
        extrHost2: extrHost2
      volumes:
        - /home/xxx
        - /home/bbb:ro
        - /home/ccc:rw
        - /home/yyy:/home/yyy
        - /home/zzz:/home/zzz:ro
        - /home/aaa:/home/aaa:rw
        - /home/aaa1:/home/aaa1234:rw
  
""")
    configHandler.setup(config)

    assertCloud('ecs-cloud', com.cloudbees.jenkins.plugins.amazonecs.ECSCloud){
        assert it.credentialsId == 'aws-cred'
        assert it.regionName == 'us-east-1'
        assert it.cluster == 'ecs-cluster'
        assert it.slaveTimoutInSeconds == 60
        assert it.jenkinsUrl == 'http://127.0.0.1:8080'
        assert it.tunnel == '127.0.0.1:8080'
        def template = it.templates[0]
        assert template.templateName == 'ecs-template'
        assert template.label == 'test generic'
        assert template.image == 'odavid/jenkins-jnlp-slave:latest'
        assert template.remoteFSRoot == '/home/jenkins'
        assert template.memory == 4000
        assert template.memoryReservation == 2000
        assert template.cpu == 512
        assert template.jvmArgs == '-Xmx1G'
        assert template.entrypoint == '/entrypoint.sh'
        assert template.logDriver == 'aws'
        assert template.dnsSearchDomains == '8.8.8.8'
        assert template.privileged
        assert ['optionA=optionAValue', 'optionB=optionBValue'] == template.logDriverOptions.collect{ 
            "${it.name}=${it.value}"
        }
        assert ['ENV1=env1Value', 'ENV2=env2Value'] == template.environments.collect{ 
            "${it.name}=${it.value}"
        }
        assert ['extrHost1=extrHost1', 'extrHost2=extrHost2'] == template.extraHosts.collect{ 
            "${it.ipAddress}=${it.hostname}"
        }
        
        def mountPoints = template.mountPoints
        def assertMountPoint = { name, sourcePath, containerPath, readOnly ->
            def mpe = mountPoints.find{it.name == configHandler.pathToVolumeName(name)}
            assert mpe.sourcePath == sourcePath
            assert mpe.containerPath == containerPath
            assert mpe.readOnly == readOnly
        }
        assertMountPoint('/home/xxx', null, '/home/xxx', false)
        assertMountPoint('/home/bbb', null, '/home/bbb', true)
        assertMountPoint('/home/ccc', null, '/home/ccc', false)
        assertMountPoint('/home/yyy', '/home/yyy', '/home/yyy', false)
        assertMountPoint('/home/zzz', '/home/zzz', '/home/zzz', true)
        assertMountPoint('/home/aaa1', '/home/aaa1', '/home/aaa1234', false)
    }
}

def testKubernetes(){
	def config = new Yaml().load("""
kube-cloud:
  type: kubernetes
  namespace: jenkins-ns
  jenkinsUrl: http://127.0.0.1:8080
  serverUrl: http://127.0.0.1:6000
  tunnel: 127.0.0.1:8080
  credentialsId: kube-cred
  skipTlsVerify: true
  serverCertificate: kube-cred
  maxRequestsPerHost: 10
  connectTimeout: 10
  retentionTimeout: 10
  readTimeout: 10
  containerCap: 10
  defaultsProviderTemplate: defaultsProviderTemplate
  templates:
    - name: kube-cloud
      namespace: jenkins-ns
      inheritFrom: general-pod
      nodeSelector: nodeSelector
      serviceAccount: jenkins-service-account
      slaveConnectTimeout: 60
      instanceCap: 10
      imagePullSecrets:
        - xxx
        - yyy
      labels:
        - generic
        - kubernetes
      image: odavid/jenkins-jnlp-template:latest
      command: /run/me
      args: x y z
      tty: true
      remoteFs: /home/jenkins
      privileged: true
      alwaysPullImage: true
      environment:
        ENV1: env1Value
        ENV2: env2Value
      ports:
        - 9090:8080
        - 1500
      resourceRequestMemory: 1024Mi
      resourceRequestCpu: 512m
      resourceLimitCpu: 1024m
      resourceLimitMemory: 512Mi
      volumes:
        - /home/xxx
        - /home/bbb:ro
        - /home/ccc:rw
        - /home/yyy:/home/yyy
        - /home/zzz:/home/zzz:ro
        - /home/aaa:/home/aaa:rw
        - /home/aaa1:/home/aaa1234:rw
      livenessProbe:
        timeoutSeconds: 10
        initialDelaySeconds: 10
        failureThreshold: 10
        periodSeconds: 10
        successThreshold: 10
""")
    configHandler.setup(config)

    assertCloud('kube-cloud', org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud){
        assert it.credentialsId == 'kube-cred'
    }
}

def testClouds(){
    testEcs()
    testKubernetes()
}

testClouds()