# Changelog

## [0.2.1](https://github.com/flashpi1/flashpi-java/compare/v0.2.0...v0.2.1) (2026-11-25)


### Bug fixes

* **models:** read certificate warnings and the API's new codes ([8c1d665](https://github.com/flashpi1/flashpi-java/commit/8c1d665e036ffd1970edf9d0061ba21f3a6edcf8))

## [0.2.0](https://github.com/flashpi1/flashpi-java/compare/v0.1.0...v0.2.0) (2026-11-18)


### Features

* **models:** let the last payment leave out its amount ([3acf712](https://github.com/flashpi1/flashpi-java/commit/3acf7128d345c2d4382c07920105f9b1575d4585))


### Dependencies

* Bump com.fasterxml.jackson.core:jackson-databind ([44d40bc](https://github.com/flashpi1/flashpi-java/commit/44d40bc5d6bcf704cf22597b50232d741ed62bfc))

## 0.1.0 (2026-11-14)


### Features

* add the client, the request engine and every operation ([2775616](https://github.com/flashpi1/flashpi-java/commit/2775616e805deee111c60b43c6350694058d1959))
* generate the models from the OpenAPI contract ([907d9c1](https://github.com/flashpi1/flashpi-java/commit/907d9c1d4392bcc4fd4b92e9d384ade6a00918c4))


### Bug fixes

* never turn contract text into code, and pin the shared enums ([7e76dde](https://github.com/flashpi1/flashpi-java/commit/7e76dde4d140947dad2e3337d9c5a14faf2b96c7))
* read answers leniently and keep models whole with any Jackson 2 mapper ([d73ca84](https://github.com/flashpi1/flashpi-java/commit/d73ca84b03539310d9b3cc24bc9ca055172267ec))
* refuse header values outside ASCII before sending ([61901a0](https://github.com/flashpi1/flashpi-java/commit/61901a0b2e672b1eac75299c5a70bad71ea35e64))
* type every failure of a call and keep its key ([35a92eb](https://github.com/flashpi1/flashpi-java/commit/35a92eb21725ba2dc2c4db4f2f4b1ba82cb4c0b4))
