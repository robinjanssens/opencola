## Creates an openjdk image that runs oc
# Windows: 
# Build docker image: "docker build -t oc ."
# Copy a valid opencola-server.yaml to %appdata%\opencola\
# Run docker image: "docker run -d -p 5795:5795 -v %appdata%\opencola\storage\:/opencola/storage -it oc"
# 
#Run on MacOS: [TBD]
#
# Run on Linux: [TBD]
# TODO: convert this all to docker-compose.yaml?

FROM openjdk:17
COPY ./opencola/ /opencola
WORKDIR /opencola/server
CMD ["sh", "bin/opencola-server", "-s", "/opencola/storage"]
EXPOSE 5795
