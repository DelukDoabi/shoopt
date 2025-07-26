# Android Development Guidelines

## General Principles
- Write code that is:
  - Easy to read, understand, maintain, extend, refactor, debug, optimize, deploy, scale, monitor, and test.
  - Clean, well-documented, and follows clean code principles.
  - Consistent with modern UI/UX practices and the app's design system.
- Always strive for:
  - Simplicity, clarity, efficiency, effectiveness, quality, reliability, maintainability, scalability, security, accessibility, usability, readability, testability, debuggability, optimizability, deployability, and monitorability.

## Android Development Expertise
- Utilize best practices, design patterns, and architecture principles.
- Leverage Android Jetpack, Kotlin, Firebase, and other modern tools.
- Ensure proper testing, debugging, optimization, deployment, scaling, and monitoring.

## UX Design Principles
- Design user interfaces that are:
  - Intuitive, visually appealing, accessible, responsive, consistent, and easy to navigate.
  - Usable across different devices and screen sizes.
- Follow modern UI/UX practices to ensure a seamless user experience.

## Coding Practices
- Always:
  - Use localization files for user-facing text to support translations and updates.
  - Keep localization files up-to-date, well-structured, and organized.
  - Use correct formats for plurals, strings, and resource identifiers as per Android documentation.
  - Maintain consistency across screens using the app's palettes and design system.
  - Add crashlytics and analytics where needed for stability and insights.
  - Refactor existing code only when necessary and without breaking functionality.

## Localization Guidelines
- When adding entries to `values/strings.xml`:
  - Use English as the default language.
  - Translate immediately to all supported languages.
  - Add entries to localization files in all languages.
  - Escape special characters to avoid issues.
  - Follow naming conventions for resource identifiers.

## Performance and Maintenance
- Ensure code is:
  - Easy to test, debug, and optimize for high performance and reliability.
  - Easy to deploy and scale for future growth.
  - Easy to monitor and maintain for long-term stability.
- Provide insights into performance and user behavior to inform future development decisions.
