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
-- Table structure for table `variant`
--

DROP TABLE IF EXISTS `variant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `variant` (
                           `id` bigint NOT NULL AUTO_INCREMENT,
                           `name` varchar(255) DEFAULT NULL,
                           `theme_id` bigint DEFAULT NULL,
                           PRIMARY KEY (`id`),
                           KEY `fk_variant_card_theme` (`theme_id`),
                           CONSTRAINT `fk_variant_card_theme` FOREIGN KEY (`theme_id`) REFERENCES `card_theme` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=705 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `variant`
--

LOCK TABLES `variant` WRITE;
/*!40000 ALTER TABLE `variant` DISABLE KEYS */;
INSERT INTO `variant` VALUES (1,'Base',1),(2,'Refractor',1),(3,'Die Cut',1),(5,'Gold',1),(6,'Silver',51),(7,'Bronze',1),(8,'Platinum',2),(9,'Diamond',3),(10,'Emerald',4),(11,'Ruby',5),(12,'Black',7),(13,'White',2),(14,'Yellow',2),(15,'Cyan',2),(16,'Magenta',2),(17,'Red',2),(18,'Blue',2),(19,'Grean',2),(101,'Electric Court Gold',8),(102,'Electric Court Platinum',8),(103,'Exclusives',8),(104,'Limited',8),(105,'Press Proof',8),(106,'Spectrum',8),(107,'Game Jersey Patch',14),(108,'Autographed Jersey',13),(109,'Black Diamond Triple',8),(110,'UD Choice Prime Cuts',8),(111,'UD Glossy',8),(112,'UD Ionix Reciprocal',8),(113,'UD Ionix Fusion',8),(114,'SP Authentic Finite',8),(115,'SPx Spectrum',8),(116,'SPx Grand Finale',8),(117,'Upper Deck Finite Radiance',8),(118,'Upper Deck Finite Spectrum',8),(119,'UD Black Finite',8),(120,'UD Black Platinum',8),(121,'UD Premier Rookie Auto Patch',13),(122,'UD Chronology Auto',13),(201,'Refractor',17),(202,'X-Fractor',17),(203,'Gold Refractor',17),(204,'Red Refractor',17),(205,'Blue Refractor',17),(206,'Green Refractor',17),(207,'Black Refractor',17),(208,'Orange Refractor',17),(209,'Superfractor (1/1)',17),(210,'Atomic Refractor',17),(211,'Pink Refractor',17),(212,'Prism Refractor',17),(213,'Sapphire Refractor',17),(214,'Gold',15),(215,'Black',15),(216,'Red',15),(217,'Blue',15),(218,'Rainbow Foil',15),(219,'Foilboard',15),(220,'Printing Plate Cyan',15),(221,'Printing Plate Magenta',15),(222,'Printing Plate Yellow',15),(223,'Printing Plate Black',15),(224,'Finest Refractor',19),(225,'Finest Gold Refractor',19),(226,'Finest Atomic Refractor',19),(227,'Finest Die-Cut',19),(228,'Finest Embossed',19),(229,'Finest Jumbos',19),(230,'Stadium Club Chrome',19),(231,'Stadium Club Chrome Refractor',19),(232,'Stadium Club Gold Parallel',19),(233,'Bowman Chrome Refractor',16),(234,'Bowman Chrome Gold Refractor',16),(235,'Topps Total Gold',19),(301,'Ultra Gold Medallion',22),(302,'Ultra Platinum Medallion',22),(303,'Flair Showcase Legacy Collection',22),(304,'Flair Showcase Row 0',22),(305,'Flair Showcase Row 1',22),(306,'Flair Showcase Row 2',22),(307,'Flair Showcase Row 3',22),(308,'Metal Universe Precious Metal Gems (PMG) Green',24),(309,'Metal Universe Precious Metal Gems (PMG) Red',24),(310,'Metal Universe Precious Metal Gems (PMG) Blue',24),(311,'Metal Universe Precious Metal Gems (PMG) Gold',24),(312,'Z-Force Rave',22),(313,'Z-Force Super Rave',22),(314,'Z-Force Big Men On Court',22),(315,'E-X Essential Credentials Future',22),(316,'E-X Essential Credentials Now',22),(317,'Fleer Metal Universe Platinum',22),(318,'Fleer Metal Universe Rubies',22),(319,'Fleer Metal Universe Emeralds',22),(320,'Fleer Ultra Platinum',22),(321,'Fleer Ultra Masterpiece (1/1)',22),(322,'Fleer Force ForceField',22),(323,'Fleer Mystique Premiere',22),(324,'Fleer Mystique Gold',22),(325,'Fleer Platinum Ruby',22),(326,'Fleer Platinum Sapphire',22),(327,'Fleer Showcase Row 4',22),(328,'Fleer Splendid Splendor',22),(329,'Fleer Hoops High Voltage',22),(330,'Fleer Hoops Spark Plugs',22),(401,'Gold',28),(402,'Silver',28),(403,'Red',28),(404,'Black',28),(405,'Prismatic',28),(406,'Numbered Parallel',28),(407,'Pulsar',28),(408,'Spectrum',28),(409,'X-Factor',28),(410,'True 1/1',30),(411,'Printing Plate',28),(412,'Sapphire',28),(413,'Ruby',28),(501,'Silver Prizm',32),(502,'Hyper Prizm',32),(503,'Red Prizm',32),(504,'Blue Prizm',32),(505,'Red White Blue Prizm',32),(506,'Green Prizm',32),(507,'Orange Prizm',32),(508,'Purple Prizm',32),(509,'Gold Prizm',32),(510,'Black Prizm',32),(511,'Mojo Prizm',32),(512,'Wave Prizm',32),(513,'Tie-Dye Prizm',32),(514,'Snakeskin Prizm',32),(515,'Neon Green Prizm',32),(516,'Choice Prizm',32),(517,'Fast Break Prizm',32),(518,'FOTL Prizm',32),(519,'Disco Prizm',32),(520,'Tiger Prizm',32),(521,'Rainbow Prizm',32),(522,'Camo Prizm',32),(523,'White Sparkle Prizm',32),(524,'Select Concourse Silver',32),(525,'Select Premier Level Silver',32),(526,'Select Courtside Silver',32),(527,'Select Zebra',32),(528,'Select Tie-Dye',32),(529,'Optic Shock',32),(530,'Optic Holo',32),(531,'Optic Gold',32),(532,'Optic Black',32),(533,'Optic Red',32),(534,'Optic Blue',32),(535,'Optic Purple',32),(536,'Optic Pink Velocity',32),(537,'Optic Black Velocity',32),(538,'Optic Green',32),(539,'Contenders Rookie Ticket Variation',36),(540,'Contenders Playoff Ticket',36),(541,'Contenders Championship Ticket',36),(542,'Contenders Super Bowl Ticket (1/1)',36),(543,'Mosaic Reactive Gold',32),(544,'Mosaic Genesis',32),(545,'Mosaic Stained Glass',32),(546,'Mosaic Peacock',32),(547,'Spectra Nebula',32),(548,'Spectra Gold',32),(549,'Noir Color',32),(550,'Noir Spotlight',32),(551,'Immaculate Dual Tags',37),(552,'National Treasures Logoman',37),(553,'National Treasures Printing Plate',37),(554,'National Treasures Laundry Tag',37),(555,'Flawless Diamond',32),(556,'Flawless Platinum (1/1)',32),(557,'Court Kings Impressionist',32),(558,'Court Kings Expressionist',32),(559,'Revolution Galactic',32),(560,'Revolution Cosmic',32),(561,'Revolution Sunburst',32),(562,'Obsidian Vitreous',32),(563,'Obsidian Electric Etch',32),(564,'Origins Gold',32),(565,'Origins Black',32),(566,'Crown Royale Silhouette',39),(567,'Crown Royale Kaboom!',39),(568,'One and One Downtown',32),(569,'One and One Gold',32),(570,'One and One Black',32),(571,'Impeccable Silver',32),(572,'Impeccable Gold',32),(573,'Certified Mirror',32),(574,'Certified Platinum',32),(575,'Threads Gold',32),(576,'Chronicles Gold',32),(577,'Chronicles Black',32),(578,'Elite Aspirations',32),(579,'Elite Status',32),(580,'Absolute Memorabilia Tools of the Trade',32),(581,'Status Blue',32),(582,'Luminance Gold',32),(583,'Playbook Nexus',32),(601,'Classic Gold',46),(602,'Classic Silver',46),(603,'Classic Blue',46),(604,'Classic Red',46),(605,'Classic Green',46),(606,'Classic Purple',46),(607,'Classic Autograph',46),(701,'Scoreboard Gold',49),(702,'Scoreboard Silver',49),(703,'Scoreboard Platinum',49),(704,'Scoreboard Autograph',50);
/*!40000 ALTER TABLE `variant` ENABLE KEYS */;
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
