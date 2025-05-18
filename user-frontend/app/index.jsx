import { Image, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import React from "react";
import { colors } from "../src/utils/colors";
import { fonts } from "../src/utils/fonts";
import { useRouter } from "expo-router";  // useRouter import edilmiştir

const HomeScreen = () => {
  const router = useRouter();  // useRouter hook'u ile yönlendirme yapılır

  const handleLogin = () => {
    router.push("/login");  // "LOGIN" ekranına yönlendirme
  };

  const handleSignup = () => {
    router.push("/signup");  // "SIGNUP" ekranına yönlendirme
  };

  const handleTrackShipment = () => {
    router.push("/shipment");  // Kargo takibi ekranına yönlendirme
  };

  return (
    <View style={styles.container}>
      <Image source={require("../src/assets/logo.png")} style={styles.logo} />
      <Image source={require("../src/assets/man.png")} style={styles.bannerImage} />
      <Text style={styles.title}>Lorem ipsum dolor.</Text>
      <Text style={styles.subTitle}>
        Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod
        tempor incididunt ut labore et dolore 
      </Text>
      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[styles.loginButtonWrapper, { backgroundColor: colors.primary }]}
          onPress={handleLogin}
        >
          <Text style={styles.loginButtonText}>Login</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.loginButtonWrapper]}
          onPress={handleSignup}
        >
          <Text style={styles.signupButtonText}>Sign-up</Text>
        </TouchableOpacity>
      </View>
      <TouchableOpacity
        style={styles.trackShipmentButtonWrapper}
        onPress={handleTrackShipment}
      >
        <Text style={styles.trackShipmentButtonText}>Kargom Nerede?</Text>
      </TouchableOpacity>
    </View>
  );
};

export default HomeScreen;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.white,
    alignItems: "center",
  },
  logo: {
    height: 40,
    width: 140,
    marginVertical: 30,
  },
  bannerImage: {
    marginVertical: 20,
    height: 250,
    width: 231,
  },
  title: {
    fontSize: 40,
    fontFamily: fonts.SemiBold,
    paddingHorizontal: 20,
    textAlign: "center",
    color: colors.primary,
    marginTop: 40,
  },
  subTitle: {
    fontSize: 18,
    paddingHorizontal: 20,
    textAlign: "center",
    color: colors.secondary,
    fontFamily: fonts.Medium,
    marginVertical: 20,
  },
  buttonContainer: {
    marginTop: 20,
    flexDirection: "row",
    borderWidth: 2,
    borderColor: colors.primary,
    width: "80%",
    height: 60,
    borderRadius: 100,
  },
  loginButtonWrapper: {
    justifyContent: "center",
    alignItems: "center",
    width: "50%",
    borderRadius: 98,
  },
  loginButtonText: {
    color: colors.white,
    fontSize: 18,
    fontFamily: fonts.SemiBold,
  },
  signupButtonText: {
    fontSize: 18,
    fontFamily: fonts.SemiBold,
  },
  trackShipmentButtonWrapper: {
    marginTop: 20,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: colors.primary,
    width: "80%",
    height: 50,
    borderRadius: 25,
  },
  trackShipmentButtonText: {
    color: colors.white,
    fontSize: 18,
    fontFamily: fonts.SemiBold,
  },
});
