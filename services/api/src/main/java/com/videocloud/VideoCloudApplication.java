package com.videocloud;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
@SpringBootApplication @EnableAsync
public class VideoCloudApplication { public static void main(String[] args){SpringApplication.run(VideoCloudApplication.class,args);} }
