import {
  Image,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
  Alert
} from "react-native";
import React, { useState } from "react";
import { useRouter } from "expo-router";
import Ionicons from "react-native-vector-icons/Ionicons";
import SimpleLineIcons from "react-native-vector-icons/SimpleLineIcons";
import { colors } from "../src/utils/colors";
import { fonts } from "../src/utils/fonts";
import axios from 'axios';

const LoginScreen = () => {
  const router = useRouter();
  const [secureEntry, setSecureEntry] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const handleLogin = async () => {
    try {
      const response = await axios.post('http://10.0.2.2:8080/api/auth/get-token', {
        username: email,
        password: password,
      });
      // Başarılı yanıt alındığında token'ı sakla veya yönlendir
      const { token } = response.data;
      Alert.alert("Başarılı", "Giriş başarılı!", [
        { text: "OK", onPress: () => router.push("/home") }, // Burada yönlendireceğin sayfa
      ]);
      // Token'ı bir yerde sakla (örneğin AsyncStorage, Redux, vs.)
      // AsyncStorage.setItem('token', token); 
    } catch (error) {
      console.error(error);
      Alert.alert("Hata", "Giriş sırasında bir hata oluştu.");
    }
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.backButtonWrapper} onPress={() => router.back()}>
        <Ionicons name="arrow-back-outline" color={colors.primary} size={25} />
      </TouchableOpacity>

      <View style={styles.textContainer}>
        <Text style={styles.headingText}>Hey,</Text>
        <Text style={styles.headingText}>Welcome</Text>
        <Text style={styles.headingText}>Back</Text>
      </View>

      {/* Form */}
      <View style={styles.formContainer}>
        <View style={styles.inputContainer}>
          <Ionicons name="mail-outline" size={30} color={colors.secondary} />
          <TextInput
            style={styles.textInput}
            placeholder="Enter your email"
            placeholderTextColor={colors.secondary}
            keyboardType="email-address"
            value={email}
            onChangeText={setEmail} // Email değişkenini güncelle
          />
        </View>

        <View style={styles.inputContainer}>
          <SimpleLineIcons name="lock" size={30} color={colors.secondary} />
          <TextInput
            style={styles.textInput}
            placeholder="Enter your password"
            placeholderTextColor={colors.secondary}
            secureTextEntry={secureEntry}
            value={password}
            onChangeText={setPassword} // Password değişkenini güncelle
          />
          <TouchableOpacity onPress={() => setSecureEntry((prev) => !prev)}>
            <SimpleLineIcons name="eye" size={20} color={colors.secondary} />
          </TouchableOpacity>
        </View>

        <TouchableOpacity>
          <Text style={styles.forgotPasswordText}>Forgot Password?</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.loginButtonWrapper} onPress={handleLogin}>
          <Text style={styles.loginText}>Login</Text>
        </TouchableOpacity>

        <View style={styles.footerContainer}>
          <Text style={styles.accountText}>Don’t have an account?</Text>
          <TouchableOpacity onPress={() => router.push("/signup")}>
            <Text style={styles.signupText}>Sign up</Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
};

export default LoginScreen;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.white,
    padding: 20,
  },
  backButtonWrapper: {
    height: 40,
    width: 40,
    backgroundColor: colors.gray,
    borderRadius: 20,
    justifyContent: "center",
    alignItems: "center",
  },
  textContainer: {
    marginVertical: 20,
  },
  headingText: {
    fontSize: 32,
    color: colors.primary,
    fontFamily: fonts.SemiBold,
  },
  formContainer: {
    marginTop: 20,
  },
  inputContainer: {
    borderWidth: 1,
    borderColor: colors.secondary,
    borderRadius: 100,
    paddingHorizontal: 20,
    flexDirection: "row",
    alignItems: "center",
    padding: 2,
    marginVertical: 10,
  },
  textInput: {
    flex: 1,
    paddingHorizontal: 10,
    fontFamily: fonts.Light,
  },
  forgotPasswordText: {
    textAlign: "right",
    color: colors.primary,
    fontFamily: fonts.SemiBold,
    marginVertical: 10,
  },
  loginButtonWrapper: {
    backgroundColor: colors.primary,
    borderRadius: 100,
    marginTop: 20,
  },
  loginText: {
    color: colors.white,
    fontSize: 20,
    fontFamily: fonts.SemiBold,
    textAlign: "center",
    padding: 10,
  },
  footerContainer: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    marginVertical: 20,
    gap: 5,
  },
  accountText: {
    color: colors.primary,
    fontFamily: fonts.Regular,
  },
  signupText: {
    color: colors.primary,
    fontFamily: fonts.Bold,
  },
});
