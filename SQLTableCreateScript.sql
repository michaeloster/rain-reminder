CREATE TABLE IF NOT EXISTS `myusers`.`users_by_phone_number` (
  `phone_number` VARCHAR(32) NOT NULL,
  `name` VARCHAR(45) NULL DEFAULT NULL,
  `zip_code` INT(5) NULL DEFAULT NULL,
  `latitude` DECIMAL(9,6) NULL DEFAULT NULL,
  `longitude` DECIMAL(9,6) NULL DEFAULT NULL,
  `city` VARCHAR(45) NULL DEFAULT NULL,
  `state` VARCHAR(45) NULL DEFAULT NULL,
  `email` VARCHAR(45) NULL DEFAULT NULL,
  `report` VARCHAR(5) NULL DEFAULT NULL,
  `report_time` VARCHAR(5) NULL DEFAULT NULL,
  PRIMARY KEY (`phone_number`))
ENGINE = InnoDB
DEFAULT CHARACTER SET = latin1