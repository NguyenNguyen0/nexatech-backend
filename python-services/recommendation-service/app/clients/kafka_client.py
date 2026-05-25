from aiokafka import AIOKafkaProducer


class KafkaClient:
    def __init__(self, bootstrap_servers: str) -> None:
        self.bootstrap_servers = bootstrap_servers

    async def ping(self) -> bool:
        producer = AIOKafkaProducer(bootstrap_servers=self.bootstrap_servers)
        await producer.start()
        try:
            return True
        finally:
            await producer.stop()
