import {
  Injectable,
  Logger,
  OnApplicationShutdown,
  OnModuleInit,
} from '@nestjs/common';

@Injectable()
export class EurekaService implements OnModuleInit, OnApplicationShutdown {
  private readonly logger = new Logger(EurekaService.name);
  private readonly enabled = /^true$/i.test(process.env.EUREKA_ENABLED ?? 'false');
  private readonly serviceName = process.env.SERVICE_NAME ?? 'promotion-service';
  private readonly appName = this.serviceName.toUpperCase().replace(/-/g, '-');
  private readonly port = Number(process.env.EUREKA_INSTANCE_PORT ?? process.env.PORT ?? 8093);
  private readonly hostName =
    process.env.EUREKA_INSTANCE_HOSTNAME ??
    process.env.EUREKA_INSTANCE_IP_ADDRESS ??
    'localhost';
  private readonly ipAddress =
    process.env.EUREKA_INSTANCE_IP_ADDRESS ??
    process.env.EUREKA_INSTANCE_HOSTNAME ??
    '127.0.0.1';
  private readonly instanceId = `${this.ipAddress}:${this.serviceName}:${this.port}`;
  private readonly eurekaBaseUrl = this.normalizeEurekaUrl(
    process.env.EUREKA_SERVER_URL ?? 'http://localhost:8761/eureka/',
  );
  private heartbeatTimer?: NodeJS.Timeout;

  async onModuleInit() {
    if (!this.enabled) return;

    await this.register();
    this.heartbeatTimer = setInterval(() => {
      this.heartbeat().catch((error) => {
        this.logger.warn(`Eureka heartbeat failed: ${error.message}`);
      });
    }, 30000);
  }

  async onApplicationShutdown() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
    }
    if (!this.enabled) return;

    await this.unregister().catch((error) => {
      this.logger.warn(`Eureka unregister failed: ${error.message}`);
    });
  }

  private async register() {
    const homePageUrl = `http://${this.hostName}:${this.port}/`;
    const healthCheckUrl = `http://${this.hostName}:${this.port}/health`;
    const response = await fetch(`${this.eurekaBaseUrl}/apps/${this.appName}`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        instance: {
          instanceId: this.instanceId,
          hostName: this.hostName,
          app: this.appName,
          ipAddr: this.ipAddress,
          vipAddress: this.serviceName,
          secureVipAddress: this.serviceName,
          status: 'UP',
          port: { $: this.port, '@enabled': 'true' },
          securePort: { $: 443, '@enabled': 'false' },
          healthCheckUrl,
          statusPageUrl: `${homePageUrl}info`,
          homePageUrl,
          dataCenterInfo: {
            '@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo',
            name: 'MyOwn',
          },
          metadata: {
            zone: 'default',
            'management.port': String(this.port),
          },
        },
      }),
    });

    if (!response.ok) {
      throw new Error(`register returned ${response.status}`);
    }
    this.logger.log(`Registered ${this.instanceId} with Eureka`);
  }

  private async heartbeat() {
    const response = await fetch(
      `${this.eurekaBaseUrl}/apps/${this.appName}/${encodeURIComponent(this.instanceId)}`,
      { method: 'PUT' },
    );

    if (response.status === 404) {
      this.logger.warn('Eureka lease missing, re-registering instance');
      await this.register();
      return;
    }

    if (!response.ok) {
      throw new Error(`heartbeat returned ${response.status}`);
    }
  }

  private async unregister() {
    const response = await fetch(
      `${this.eurekaBaseUrl}/apps/${this.appName}/${encodeURIComponent(this.instanceId)}`,
      { method: 'DELETE' },
    );
    if (!response.ok && response.status !== 404) {
      throw new Error(`unregister returned ${response.status}`);
    }
  }

  private normalizeEurekaUrl(url: string) {
    return url.replace(/\/+$/, '');
  }
}
