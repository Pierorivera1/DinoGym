# AWS Emulator, pinned (decision 57). AWS publishes no image, only RPM and
# Debian packages, so we build one. To upgrade: pick a version from
# https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/versions.json,
# verify its .sig (docs/WORKFLOW.md), then update EMULATOR_VERSION and the checksum.
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal@sha256:158a9044f9dc0777f098b76c8b54346f6ed4560b9a6a416dec7a5ba3bc483af4

ARG EMULATOR_VERSION=1.0.491
ADD --checksum=sha256:2a0be8455d14e1051c3414093302de010bdf36dd165d665164d22f8317ee72c2 \
    https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/aws-emulator-${EMULATOR_VERSION}-1-x86_64.rpm \
    /tmp/aws-emulator.rpm
RUN rpm -i /tmp/aws-emulator.rpm && rm /tmp/aws-emulator.rpm \
    && mkdir -p /home/emulator && chown 1000 /home/emulator
USER 1000
ENV HOME=/home/emulator
EXPOSE 9297
# Listen on all addresses inside the container; the run script publishes the
# port on 127.0.0.1 only.
ENTRYPOINT ["aws-emulator", "-b", "0.0.0.0"]
