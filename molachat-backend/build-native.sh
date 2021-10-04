echo "start building native application"
mvn -Pnative-image package -f pom-native.xml
