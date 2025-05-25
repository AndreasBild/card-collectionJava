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
-- Table structure for table `card_brand`
--

DROP TABLE IF EXISTS `card_brand`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `card_brand` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `manufacturer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsq6h1hcxdvf1dn4vxwepxrbso` (`manufacturer_id`),
  CONSTRAINT `fk_cardbrand_manufacturer` FOREIGN KEY (`manufacturer_id`) REFERENCES `card_manufacturer` (`id`) ON DELETE SET NULL,
  CONSTRAINT `FKsq6h1hcxdvf1dn4vxwepxrbso` FOREIGN KEY (`manufacturer_id`) REFERENCES `card_manufacturer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_brand`
--

LOCK TABLES `card_brand` WRITE;
/*!40000 ALTER TABLE `card_brand` DISABLE KEYS */;
INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(2,'Exquisite',1),(3,'SP Authentic',1),(4,'Upper Deck',1),(5,'SP',1),(6,'SP Championship	',1),(7,'UD3	',1),(8,'SPx',1),(9,'Hardcourt',1),(10,'Black Diamond',1),(11,'SPx Finite',1),(12,'Choice',1),(13,'Ionix',1),(14,'Ovation',1),(15,'Encore',1),(16,'HoloGrFX',1),(17,'Retro',1),(18,'MVP',1),(19,'Gold Reserve',1),(20,'Victory',1),(21,'Reserve',1),(22,'UDx',1),(23,'SLAM',1),(24,'SP Game Used',1),(25,'Glass',1),(26,'Authentics ',1),(27,'Sweet Shot',1),(28,'Ultimate Victory',1),(29,'Honor Roll',1),(30,'Inspiration',1),(31,'SP Authentic Limited',1),(32,'Flight Team',1),(33,'Finite',1),(34,'Ultimate Collection',1),(35,'Championship Drive ',1),(36,'Exclusives',1),(37,'Standing O',1),(38,'Legends',1),(39,'R-Class',1),(40,'Trilogy',1),(41,'Reflections',1),(42,'ESPN',1),(43,'Rookie Debut',1),(44,'Signature Edition',1),(50,'Topps',2),(51,'Embossed',2),(52,'Finest',2),(53,'Stadium Club',2),(54,'Stadium Club Members Only',2),(55,'Gallery',2),(56,'Bowman\'s Best	',2),(57,'Chrome',2),(58,'Gold Label',2),(59,'Tip Off',2),(60,'Heritage',2),(61,'Stars',2),(62,'Reserve',2),(63,'Pristine',2),(64,'Jersey Edition',2),(65,'Bazooka',2),(66,'Turkey Red',2),(67,'Contemporary Collection',2),(68,'Rookie Matrix',2),(69,'First Edition',2),(70,'Luxury Box',2),(71,'Total',2),(80,'Flair',3),(81,'Fleer',3),(82,'Jam Session',3);
/*!40000 ALTER TABLE `card_brand` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-05-25 13:18:11
