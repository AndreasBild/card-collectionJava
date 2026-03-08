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
) ENGINE=InnoDB AUTO_INCREMENT=175 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `card_brand`
--

LOCK TABLES `card_brand` WRITE;
/*!40000 ALTER TABLE `card_brand` DISABLE KEYS */;
INSERT INTO `card_brand` VALUES (1,'Collectors Choice',1),(2,'Exquisite',1),(3,'SP Authentic',1),(4,'Upper Deck',1),(5,'SP',1),(6,'SP Championship',1),(7,'UD3',1),(8,'SPx',1),(9,'Hardcourt',1),(10,'Black Diamond',1),(11,'SPx Finite',1),(12,'Choice',1),(13,'Ionix',1),(14,'Ovation',1),(15,'Encore',1),(16,'HoloGrFX',1),(17,'Retro',1),(18,'MVP',1),(19,'Gold Reserve',1),(20,'Victory',1),(22,'UDx',1),(23,'SLAM',1),(24,'SP Game Used',1),(25,'Glass',1),(26,'Authentics',1),(27,'Sweet Shot',1),(28,'Ultimate Victory',1),(29,'Honor Roll',1),(30,'Inspiration',1),(31,'SP Authentic Limited',1),(32,'Flight Team',1),(33,'Finite',1),(34,'Ultimate Collection',1),(35,'Championship Drive',1),(36,'Exclusives',1),(37,'Standing O',1),(38,'Legends',1),(39,'R-Class',1),(40,'Trilogy',1),(41,'Reflections',1),(42,'ESPN',1),(43,'Rookie Debut',1),(44,'Signature Edition',1),(50,'Topps',2),(51,'Topps Embossed',2),(55,'Topps Gallery',2),(56,'Topps Bowman\'s Best',2),(58,'Topps Gold Label',2),(59,'Topps Tip Off',2),(60,'Topps Heritage',2),(61,'Topps Stars',2),(62,'Topps Reserve',2),(63,'Topps Pristine',2),(64,'Topps Jersey Edition',2),(65,'Topps Bazooka',2),(66,'Topps Turkey Red',2),(67,'Topps Contemporary Collection',2),(68,'Topps Rookie Matrix',2),(69,'Topps First Edition',2),(70,'Topps Luxury Box',2),(71,'Topps Total',2),(80,'Fleer Flair',3),(81,'Fleer',3),(82,'Fleer Jam Session',3),(83,'SkyBox',3),(84,'SkyBox Premium',3),(85,'SkyBox Autographics',3),(86,'SkyBox E-X',3),(87,'SkyBox Z-Force',3),(88,'SkyBox Hoops',3),(89,'SkyBox LE',3),(90,'SkyBox Dominion',3),(91,'SkyBox Thunder',3),(92,'SkyBox Metal Universe',3),(93,'SkyBox Molten Metal',3),(94,'SkyBox Apex',3),(95,'SkyBox Holographics',3),(96,'SkyBox Impact',3),(97,'SkyBox Jam Session',3),(98,'Donruss',5),(99,'Donruss Optic',5),(100,'Panini Prizm',5),(101,'Panini Select',5),(102,'Panini Contenders',5),(103,'Panini Contenders Optic',5),(104,'Panini National Treasures',5),(105,'Panini Noir',5),(106,'Panini Immaculate',5),(107,'Panini Impeccable',5),(108,'Panini Court Kings',5),(109,'Panini Revolution',5),(110,'Panini Origins',5),(111,'Panini Mosaic',5),(112,'Panini Chronicles',5),(113,'Panini One and One',5),(114,'Panini Obsidian',5),(115,'Panini Absolute Memorabilia',5),(116,'Panini Illusions',5),(117,'Panini Spectra',5),(118,'Panini Dominion',5),(119,'Panini PhotoGenic',5),(120,'Panini Instant',5),(121,'Panini Draft Picks',5),(122,'Panini Hoops',5),(123,'Leaf Metal',4),(124,'Leaf Valiant',4),(125,'Leaf Ultimate Draft',4),(126,'Leaf Trinity',4),(127,'Leaf Best of Basketball',4),(128,'Leaf In The Game Used',4),(129,'Leaf Signature Series',4),(130,'Topps Chrome',2),(131,'Topps Finest',2),(132,'Topps Stadium Club',2),(133,'Topps Bazooka',2),(134,'Topps Total',2),(135,'Topps Midnight',2),(136,'SP Signature Edition',1),(137,'SP Top Prospects',1),(138,'SP Rookie Threads',1),(139,'UD Reserve',1),(140,'UD Black',1),(141,'UD Premier',1),(142,'UD Chronology',1),(143,'SkyBox Metal Universe Champions',1),(144,'Fleer Ultra',3),(145,'Fleer Metal',3),(146,'Fleer Tradition',3),(147,'Fleer Authority',3),(148,'Fleer Brilliants',3),(149,'Fleer Force',3),(150,'Fleer Mystique',3),(151,'Fleer Platinum',3),(152,'Fleer Showcase',3),(153,'Fleer Splendid',3),(154,'Leaf Limited',4),(155,'Leaf Originals',4),(156,'Scoreboard',7),(157,'Scoreboard Autographed Collection',7),(158,'Scoreboard Signature Series',7),(159,'Classic',6),(160,'Classic Draft Picks',6),(161,'Classic Games',6),(162,'Classic Images',6),(163,'Classic Road to the NBA',6),(164,'Topps Bowman',2),(165,'Topps Allen & Ginter',2),(166,'Topps Draft Picks & Prospects',2),(167,'Panini Crown Royale',5),(168,'Panini Prime',5),(169,'Panini Gold Standard',5),(170,'Panini Vanguard',5),(171,'Panini Encased',5),(172,'Panini Status',5),(173,'Panini Luminance',5),(174,'Panini Playbook',5);
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

-- Dump completed on 2025-05-27 16:44:59
