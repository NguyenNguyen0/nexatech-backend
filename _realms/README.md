@nvminh162:

+ file json config keycloak đã gắn hard secret key là gUZhl1gcgZgpLbzzvXwnExf8DTnK8YuG 
  => production gọi API của keycloak để tự generate secret key để tăng bảo mật khi triển khai

+ khi setup môi trường tham khảo file ./docker-compose.yml để tự setup tuỳ chỉnh docker cá nhân để khớp môi trường chạy dự án

+ khi docker compose up -d => đã tự động import setting của keycloak

+ khi chạy idenity service
  khi cấu hình môi trường cho keycloak gắn thêm (thao khảo)
        {
          "CONFIG_SERVER_URI": "http://localhost:8888",
          "KEYCLOAK_ISSUER_URI": "http://localhost:9000/realms/nexatech",
          "KEYCLOAK_URL": "http://localhost:9000",
          "KEYCLOAK_REALM": "nexatech",
          "KEYCLOAK_CLIENT_ID": "nexatech-id",
          "KEYCLOAK_CLIENT_SECRET": "gUZhl1gcgZgpLbzzvXwnExf8DTnK8YuG",
          "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092",
          "KAFKA_GROUP_ID": "identity-service-group",
          "EUREKA_INSTANCE_HOSTNAME": "localhost",
          "EUREKA_ENABLED": "true",
          "EUREKA_CLIENT_SERVICE_URL_DEFAULT_ZONE": "http://localhost:8761/eureka/"
        },

DONE
