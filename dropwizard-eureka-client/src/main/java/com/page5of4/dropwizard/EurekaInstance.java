package com.page5of4.dropwizard;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.CloudInstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.server.ServerFactory;
import org.apache.commons.configuration.BaseConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EurekaInstance implements Managed {
   private static final String EUREKA_DATACENTER_TYPE_PROP_NAME = "datacenter.type";
   private static final DynamicPropertyFactory INSTANCE = com.netflix.config.DynamicPropertyFactory.getInstance();
   private static final Logger logger = LoggerFactory.getLogger(EurekaInstance.class);
   private final ConfiguresEurekaClient configuration;
   private final EurekaClientHealthCheck healthCheck;
   private final String eurekaNamespace = "eureka.";
   private final String datacenterType;

   public EurekaInstance(ConfiguresEurekaClient configuration, EurekaClientHealthCheck healthCheck) {
      this.configuration = configuration;
      this.healthCheck = healthCheck;
      this.datacenterType = INSTANCE.getStringProperty(eurekaNamespace + EUREKA_DATACENTER_TYPE_PROP_NAME, "developer").get();
   }

   @Override
   public void start() throws Exception {
      EurekaClientConfiguration eurekaClientConfiguration = configuration.getEureka();
      ServerFactory serverFactory = configuration.getServerFactory();

      BaseConfiguration baseConfiguration = new BaseConfiguration();
      baseConfiguration.setProperty(eurekaNamespace + "name", eurekaClientConfiguration.getName());
      baseConfiguration.setProperty(eurekaNamespace + "vipAddress", eurekaClientConfiguration.getVipAddress());
      baseConfiguration.setProperty(eurekaNamespace + "serviceUrl.default", eurekaClientConfiguration.getDefaultServiceUrl());
      baseConfiguration.setProperty(eurekaNamespace + "port", eurekaClientConfiguration.getPort());

      Integer port = DropwizardServerHelpers.getPort(serverFactory);
      Integer adminPort = DropwizardServerHelpers.getAdminPort(serverFactory);

      baseConfiguration.setProperty(eurekaNamespace + "healthCheckUrl", String.format("http://${eureka.hostname}:%d/healthcheck", adminPort));
      baseConfiguration.setProperty(eurekaNamespace + "secureHealthCheckUrl", String.format("http://${eureka.hostname}:%d/healthcheck", adminPort));
      baseConfiguration.setProperty(eurekaNamespace + "statusPageUrl", String.format("http://${eureka.hostname}:%d/healthcheck", adminPort));
      ConfigurationManager.loadPropertiesFromConfiguration(baseConfiguration);

      EurekaInstanceConfig eurekaInstanceConfig = createEurekaInstanceConfig();

      DiscoveryManager.getInstance().initComponent(eurekaInstanceConfig, new DefaultEurekaClientConfig(eurekaNamespace));

      DiscoveryManager.getInstance().getDiscoveryClient().registerHealthCheckCallback(healthCheck);

      markAsUp();
   }

   protected EurekaInstanceConfig createEurekaInstanceConfig() {
      switch(datacenterType.toLowerCase()) {
      case "amazon":
         return new CloudInstanceConfig(eurekaNamespace);
      case "developer":
         return new DeveloperMachineDataCenterInstanceConfig(eurekaNamespace);
      default:
         return new MyDataCenterInstanceConfig(eurekaNamespace);
      }
   }

   @Override
   public void stop() throws Exception {
      markAsDown();
   }

   public void markAsUp() {
      ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.UP);
   }

   public void markAsDown() {
      ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
   }
}
