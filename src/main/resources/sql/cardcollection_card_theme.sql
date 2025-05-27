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
-- Table structure for table `card_theme`
--

DROP TABLE IF EXISTS `card_theme`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_theme` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `name` varchar(255) NOT NULL,
                              `brand_id` bigint DEFAULT NULL,
                              PRIMARY KEY (`id`),
                              KEY `fk_cardtheme_brand` (`brand_id`),
                              CONSTRAINT `fk_cardtheme_brand` FOREIGN KEY (`brand_id`) REFERENCES `card_brand` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_theme`
--

LOCK TABLES `card_theme` WRITE;
/*!40000 ALTER TABLE `card_theme` DISABLE KEYS */;
INSERT INTO `card_theme` VALUES (1,'You Crash The Game Rookie Scoring',1),(2,'You Crash The Game Rookie Scoring Redemption',1),(3,'You Crash The Game Rookie Rebounds',1),(4,'Lottery Pick',1),(5,'Base Set',51),(6,'Collegiate Best',4),(7,'Rack Pack',4),(8,'Base Set',4),(9,'Rookies',4),(10,'Star Rookies',4),(11,'All-Stars',4),(12,'Checklist',4),(13,'Autographs',4),(14,'Memorabilia',4),(15,'Base Set',50),(16,'Rookies',50),(17,'Refractors',130),(18,'Parallels',130),(19,'Inserts',50),(20,'Autographs',50),(21,'Memorabilia',50),(22,'Base Set',81),(23,'Rookies',81),(24,'Precious Metals',92),(25,'Jambalaya',81),(26,'Autographs',81),(27,'Memorabilia',81),(28,'Base Set',123),(29,'Rookies',123),(30,'Certified Autographs',129),(31,'Game Used Memorabilia',128),(32,'Base Set',100),(33,'Rookies',100),(34,'Prizm Parallels',100),(35,'Color Blasts',100),(36,'Autographs',100),(37,'Memorabilia',100),(38,'Short Prints',100),(39,'Die-Cuts',167),(40,'Red Wave',100),(41,'Blue Shimmer',100),(42,'Gold Vinyl',100),(43,'Black Prizm',100),(44,'Downtown',100),(45,'Kaboom!',100),(46,'Base Set',159),(47,'Rookies',159),(48,'Autographs',159),(49,'Base Set',156),(50,'Autographs',157),(51,'Signature',1);
/*!40000 ALTER TABLE `card_theme` ENABLE KEYS */;
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
