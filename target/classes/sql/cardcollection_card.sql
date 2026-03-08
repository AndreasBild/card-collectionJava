CREATE DATABASE  IF NOT EXISTS `cardcollection` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `cardcollection`;
-- MySQL dump 10.13  Distrib 8.0.38, for macos14 (arm64)
--
-- Host: localhost    Database: cardcollection
-- ------------------------------------------------------
-- Server version	9.0.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `card`
--

DROP TABLE IF EXISTS `card`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card` (
                        `id` bigint NOT NULL AUTO_INCREMENT,
                        `print_run` int NOT NULL,
                        `serial_number` int NOT NULL,
                        `number` varchar(255) DEFAULT NULL,
                        `rookie_card` tinyint(1) NOT NULL DEFAULT '0',
                        `game_used_material` tinyint(1) NOT NULL DEFAULT '0',
                        `player_id` bigint DEFAULT NULL,
                        `theme_id` bigint DEFAULT NULL,
                        `autograph` tinyint(1) NOT NULL DEFAULT '0',
                        `season_id` bigint DEFAULT NULL,
                        `variant_id` bigint DEFAULT NULL,
                        `season` varchar(255) DEFAULT NULL,
                        PRIMARY KEY (`id`),
                        KEY `FKbyb0u8pl0bms3a11dql17ut0b` (`player_id`),
                        KEY `FKrhm60fo96t7r89farfjnmg0n9` (`theme_id`),
                        KEY `FK6xhb82f364llei3se8shqvxoa` (`variant_id`),
                        KEY `FK_card_season` (`season_id`),
                        CONSTRAINT `FK6xhb82f364llei3se8shqvxoa` FOREIGN KEY (`variant_id`) REFERENCES `variant` (`id`),
                        CONSTRAINT `FK_card_season` FOREIGN KEY (`season_id`) REFERENCES `season` (`id`),
                        CONSTRAINT `FKbyb0u8pl0bms3a11dql17ut0b` FOREIGN KEY (`player_id`) REFERENCES `player` (`id`),
                        CONSTRAINT `FKrhm60fo96t7r89farfjnmg0n9` FOREIGN KEY (`theme_id`) REFERENCES `card_theme` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1047 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card`
--

LOCK TABLES `card` WRITE;
/*!40000 ALTER TABLE `card` DISABLE KEYS */;
INSERT INTO `card` VALUES (1,0,0,'278',1,0,1,51,1,1,6,NULL),(2,0,0,'278',1,0,1,51,1,1,5,NULL),(3,750,689,'278',1,1,1,51,1,1,1,NULL);
/*!40000 ALTER TABLE `card` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-05-27 16:44:59
